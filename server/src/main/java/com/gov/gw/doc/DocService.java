package com.gov.gw.doc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gov.gw.common.ApiException;
import com.gov.gw.common.Jsons;
import com.gov.gw.flow.FlowConfig;
import com.gov.gw.flow.FlowNodeCfg;
import com.gov.gw.flow.FlowRepo;
import com.gov.gw.flow.FlowService;
import com.gov.gw.notify.NotificationService;
import com.gov.gw.org.OrgEntity;
import com.gov.gw.org.OrgRepo;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import com.gov.gw.pdf.RedHeadPdfService;
import com.gov.gw.seal.PdfSealService;
import com.gov.gw.seal.Seal;
import com.gov.gw.seal.SealRecord;
import com.gov.gw.seal.SealRecordRepo;
import com.gov.gw.seal.SealService;
import com.gov.gw.storage.StorageService;
import com.gov.gw.template.DocTemplate;
import com.gov.gw.template.TemplateRepo;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceModificationBuilder;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 公文流转核心服务。
 * 与 Camunda 的协作采用“动作后同步”模式：所有流程动作（提交/办理/退回）都由本服务发起，
 * 动作完成后调用 syncTasks 将引擎中的活动任务对账到 gw_doc_task（待办/已办），
 * 避免依赖引擎内部监听器，行为确定、便于测试。
 */
@Service
public class DocService {
    private final DocumentRepo documentRepo;
    private final DocTaskRepo taskRepo;
    private final DocTraceRepo traceRepo;
    private final TemplateRepo templateRepo;
    private final FlowRepo flowRepo;
    private final FlowService flowService;
    private final UserRepo userRepo;
    private final OrgRepo orgRepo;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final NumberService numberService;
    private final NotificationService notificationService;
    private final PermissionService permissionService;
    private final RedHeadPdfService redHeadPdfService;
    private final StorageService storage;
    private final SealService sealService;
    private final SealRecordRepo sealRecordRepo;
    private final PdfSealService pdfSealService;

    public DocService(DocumentRepo documentRepo, DocTaskRepo taskRepo, DocTraceRepo traceRepo,
                      TemplateRepo templateRepo, FlowRepo flowRepo, FlowService flowService,
                      UserRepo userRepo, OrgRepo orgRepo,
                      RuntimeService runtimeService, TaskService taskService,
                      NumberService numberService, NotificationService notificationService,
                      PermissionService permissionService, RedHeadPdfService redHeadPdfService,
                      StorageService storage, SealService sealService,
                      SealRecordRepo sealRecordRepo, PdfSealService pdfSealService) {
        this.documentRepo = documentRepo;
        this.taskRepo = taskRepo;
        this.traceRepo = traceRepo;
        this.templateRepo = templateRepo;
        this.flowRepo = flowRepo;
        this.flowService = flowService;
        this.userRepo = userRepo;
        this.orgRepo = orgRepo;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.numberService = numberService;
        this.notificationService = notificationService;
        this.permissionService = permissionService;
        this.redHeadPdfService = redHeadPdfService;
        this.storage = storage;
        this.sealService = sealService;
        this.sealRecordRepo = sealRecordRepo;
        this.pdfSealService = pdfSealService;
    }

    // ==================== 拟稿 ====================

    public record DocReq(String title, Long templateId, Integer secretLevel,
                         String mainSend, String copySend, String content,
                         List<Map<String, Object>> attachments) {}

    @Transactional
    public Document createDraft(DocReq req, UserEntity user) {
        DocTemplate template = templateRepo.findById(req.templateId())
                .orElseThrow(() -> new ApiException("模板不存在"));
        if (!Boolean.TRUE.equals(template.getEnabled())) throw new ApiException("模板已停用");
        Document doc = new Document();
        applyReq(doc, req);
        doc.setTemplateId(template.getId());
        doc.setFlowId(template.getFlowId());
        doc.setCreatedBy(user.getId());
        documentRepo.save(doc);
        trace(doc, user, "CREATE", null, null, "拟稿");
        return doc;
    }

    @Transactional
    public Document updateDraft(Long id, DocReq req, UserEntity user) {
        Document doc = getDoc(id);
        if (!doc.getCreatedBy().equals(user.getId())) throw ApiException.forbidden("仅拟稿人可修改");
        if (!Document.STATUS_DRAFT.equals(doc.getStatus()) && !Document.STATUS_RETURNED.equals(doc.getStatus())) {
            throw new ApiException("当前状态不可修改");
        }
        applyReq(doc, req);
        doc.setUpdatedAt(LocalDateTime.now());
        return documentRepo.save(doc);
    }

    private void applyReq(Document doc, DocReq req) {
        if (req.title() == null || req.title().isBlank()) throw new ApiException("标题不能为空");
        doc.setTitle(req.title().trim());
        doc.setSecretLevel(req.secretLevel() == null ? 1 : req.secretLevel());
        doc.setMainSend(req.mainSend());
        doc.setCopySend(req.copySend());
        doc.setContent(req.content());
        doc.setAttachmentsJson(req.attachments() == null ? "[]" : Jsons.write(req.attachments()));
    }

    // ==================== 提交 / 重新提交 ====================

    @Transactional
    public Document submit(Long id, UserEntity user) {
        Document doc = getDoc(id);
        if (!doc.getCreatedBy().equals(user.getId())) throw ApiException.forbidden("仅拟稿人可提交");
        if (!Document.STATUS_DRAFT.equals(doc.getStatus()) && !Document.STATUS_RETURNED.equals(doc.getStatus())) {
            throw new ApiException("当前状态不能提交");
        }
        DocTemplate template = templateRepo.findById(doc.getTemplateId())
                .orElseThrow(() -> new ApiException("模板不存在"));
        FlowConfig flow = flowRepo.findById(template.getFlowId())
                .orElseThrow(() -> new ApiException("流程不存在"));
        if (!Boolean.TRUE.equals(flow.getEnabled())) throw new ApiException("流程已停用");
        List<FlowNodeCfg> nodes = flowService.parseNodes(flow.getNodesJson());

        // 解析每个节点的办理人，作为流程变量传入
        Map<String, Object> vars = new HashMap<>();
        vars.put("docId", doc.getId());
        for (FlowNodeCfg node : nodes) {
            List<Long> assignees = resolveAssignees(node);
            vars.put("users_" + node.getKey(), assignees.stream().map(String::valueOf).toList());
        }

        doc.setFlowId(flow.getId());
        doc.setFlowSnapshotJson(Jsons.write(nodes));
        doc.setAttempt(doc.getAttempt() + 1);
        doc.setStatus(Document.STATUS_RUNNING);
        doc.setSubmittedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                FlowService.processKey(flow.getId()), String.valueOf(doc.getId()), vars);
        doc.setProcessInstanceId(pi.getId());
        documentRepo.save(doc);

        trace(doc, user, doc.getAttempt() > 1 ? "RESUBMIT" : "SUBMIT", null, null,
                doc.getAttempt() > 1 ? "补正后重新提交（第 " + doc.getAttempt() + " 次）" : "提交进入流转");
        syncTasks(doc);
        return doc;
    }

    private List<Long> resolveAssignees(FlowNodeCfg node) {
        List<Long> ids = switch (node.getAssigneeType()) {
            case "USERS" -> node.getUserIds() == null ? List.<Long>of() : node.getUserIds();
            case "POST" -> userRepo.findByEnabledTrue().stream()
                    .filter(u -> u.hasPost(node.getPost()))
                    .map(UserEntity::getId).toList();
            case "ORG_LEADER" -> {
                OrgEntity org = orgRepo.findById(node.getOrgId())
                        .orElseThrow(() -> new ApiException("节点「" + node.getName() + "」指定的部门不存在"));
                if (org.getLeaderId() == null) {
                    throw new ApiException("节点「" + node.getName() + "」的部门未设置负责人");
                }
                yield List.of(org.getLeaderId());
            }
            default -> throw new ApiException("未知办理人类型：" + node.getAssigneeType());
        };
        Set<Long> enabled = new HashSet<>();
        userRepo.findAllById(ids).forEach(u -> {
            if (Boolean.TRUE.equals(u.getEnabled())) enabled.add(u.getId());
        });
        List<Long> result = ids.stream().filter(enabled::contains).distinct().toList();
        if (result.isEmpty()) {
            throw new ApiException("节点「" + node.getName() + "」未解析到可用办理人");
        }
        return result;
    }

    // ==================== 任务对账 ====================

    /** 将 Camunda 活动任务对账到待办表，并维护公文当前节点；进入签发节点时分配文号并套红 */
    @Transactional
    public void syncTasks(Document doc) {
        if (doc.getProcessInstanceId() == null) return;
        List<Task> active = taskService.createTaskQuery()
                .processInstanceId(doc.getProcessInstanceId()).active().list();
        Set<String> activeIds = active.stream().map(Task::getId).collect(Collectors.toSet());

        for (DocTask t : taskRepo.findByDocIdAndAttemptAndStatus(
                doc.getId(), doc.getAttempt(), DocTask.STATUS_PENDING)) {
            if (!activeIds.contains(t.getCamundaTaskId())) {
                t.setStatus(DocTask.STATUS_CANCELED);
                taskRepo.save(t);
            }
        }

        List<FlowNodeCfg> nodes = snapshotNodes(doc);
        Map<String, String> names = nodes.stream()
                .collect(Collectors.toMap(FlowNodeCfg::getKey, FlowNodeCfg::getName, (a, b) -> a));

        for (Task ct : active) {
            if (ct.getAssignee() == null) continue;
            if (taskRepo.findByCamundaTaskId(ct.getId()).isPresent()) continue;
            DocTask t = new DocTask();
            t.setDocId(doc.getId());
            t.setNodeKey(ct.getTaskDefinitionKey());
            t.setNodeName(names.getOrDefault(ct.getTaskDefinitionKey(), ct.getTaskDefinitionKey()));
            t.setAssigneeId(Long.parseLong(ct.getAssignee()));
            t.setCamundaTaskId(ct.getId());
            t.setAttempt(doc.getAttempt());
            taskRepo.save(t);
            notificationService.send(t.getAssigneeId(), Notification.TYPE_TODO, "新的待办公文",
                    "《" + doc.getTitle() + "》待您办理（" + t.getNodeName() + "）", doc.getId());
        }

        if (!active.isEmpty()) {
            String key = active.get(0).getTaskDefinitionKey();
            doc.setCurrentNodeKey(key);
            doc.setCurrentNodeName(names.getOrDefault(key, key));
        } else {
            doc.setCurrentNodeKey(null);
            doc.setCurrentNodeName(null);
        }
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepo.save(doc);

        // 进入签发节点：分配发文字号 + 生成套红 PDF
        if (doc.getCurrentNodeKey() != null && doc.getDocNo() == null) {
            nodes.stream()
                    .filter(n -> n.getKey().equals(doc.getCurrentNodeKey()))
                    .filter(n -> FlowService.TYPE_ISSUE.equals(n.getType()))
                    .findFirst()
                    .ifPresent(n -> prepareIssuePdf(doc));
        }
    }

    private void prepareIssuePdf(Document doc) {
        DocTemplate template = templateRepo.findById(doc.getTemplateId())
                .orElseThrow(() -> new ApiException("模板不存在"));
        String docNo = numberService.nextNumber(template);
        doc.setDocNo(docNo);
        byte[] pdf = redHeadPdfService.generate(doc, template);
        String key = "doc/" + doc.getId() + "/pdf/redhead-a" + doc.getAttempt() + ".pdf";
        storage.put(key, new ByteArrayInputStream(pdf), pdf.length);
        doc.setPdfKey(key);
        documentRepo.save(doc);
        trace(doc, null, "ISSUE_PREP", doc.getCurrentNodeName(), null,
                "分配发文字号 " + docNo + "，生成套红文件");
    }

    // ==================== 办理 ====================

    @Transactional
    public Document complete(Long docId, Long taskId, String comment, UserEntity user) {
        Document doc = getDoc(docId);
        requireRunning(doc);
        DocTask t = requireMyPendingTask(taskId, docId, user);
        Task ct = taskService.createTaskQuery().taskId(t.getCamundaTaskId()).singleResult();
        if (ct == null) {
            syncTasks(doc);
            throw new ApiException("任务已被办理或取消，请刷新后重试");
        }
        t.setStatus(DocTask.STATUS_DONE);
        t.setAction("APPROVE");
        t.setComment(comment);
        t.setDoneAt(LocalDateTime.now());
        taskRepo.save(t);
        trace(doc, user, "APPROVE", t.getNodeName(), comment, null);
        taskService.complete(t.getCamundaTaskId());
        afterAction(doc);
        return getDoc(docId);
    }

    @Transactional
    public Document returnPrev(Long docId, Long taskId, String comment, UserEntity user) {
        Document doc = getDoc(docId);
        requireRunning(doc);
        DocTask t = requireMyPendingTask(taskId, docId, user);
        List<FlowNodeCfg> nodes = snapshotNodes(doc);
        int idx = indexOfNode(nodes, t.getNodeKey());
        if (idx < 0) throw new ApiException("节点不在流程中");
        if (idx == 0) {
            return doReturnDraft(doc, t, comment, user);
        }
        FlowNodeCfg prev = nodes.get(idx - 1);
        markNodeTasksReturned(doc, t, "RETURN_PREV", comment);
        trace(doc, user, "RETURN_PREV", t.getNodeName(), comment, "退回至「" + prev.getName() + "」");

        String pid = doc.getProcessInstanceId();
        List<String> cancelIds = findActivityInstanceIds(pid, t.getNodeKey());
        if (cancelIds.isEmpty()) {
            syncTasks(doc);
            throw new ApiException("流程状态已变化，请刷新后重试");
        }
        ProcessInstanceModificationBuilder modification = runtimeService.createProcessInstanceModification(pid);
        for (String cancelId : cancelIds) {
            modification.cancelActivityInstance(cancelId);
        }
        // 所有节点均为多实例：须启动其 miBody 作用域，引擎才会按集合变量展开办理人
        modification.startBeforeActivity(prev.getKey() + "#multiInstanceBody");
        modification.execute();

        syncTasks(doc);
        return getDoc(docId);
    }

    @Transactional
    public Document returnDraft(Long docId, Long taskId, String comment, UserEntity user) {
        Document doc = getDoc(docId);
        requireRunning(doc);
        DocTask t = requireMyPendingTask(taskId, docId, user);
        return doReturnDraft(doc, t, comment, user);
    }

    /** 退回拟稿人补正：终止本次流程实例，已取文号作废（不回收），补正后重新提交走新实例 */
    private Document doReturnDraft(Document doc, DocTask actingTask, String comment, UserEntity user) {
        markNodeTasksReturned(doc, actingTask, "RETURN_DRAFT", comment);
        String voidedNo = doc.getDocNo();
        trace(doc, user, "RETURN_DRAFT", actingTask.getNodeName(), comment,
                voidedNo != null ? "退回拟稿补正，文号 " + voidedNo + " 作废" : "退回拟稿补正");
        runtimeService.deleteProcessInstance(doc.getProcessInstanceId(), "退回拟稿补正");
        doc.setStatus(Document.STATUS_RETURNED);
        doc.setCurrentNodeKey(null);
        doc.setCurrentNodeName(null);
        doc.setProcessInstanceId(null);
        doc.setDocNo(null);
        doc.setPdfKey(null);
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepo.save(doc);
        notificationService.send(doc.getCreatedBy(), Notification.TYPE_RETURN, "公文被退回",
                "《" + doc.getTitle() + "》被退回补正" + (comment != null ? "：" + comment : ""), doc.getId());
        return doc;
    }

    private void markNodeTasksReturned(Document doc, DocTask actingTask, String action, String comment) {
        for (DocTask pt : taskRepo.findByDocIdAndAttemptAndStatus(
                doc.getId(), doc.getAttempt(), DocTask.STATUS_PENDING)) {
            if (pt.getNodeKey().equals(actingTask.getNodeKey())) {
                pt.setStatus(DocTask.STATUS_RETURNED);
                pt.setAction(action);
                if (pt.getId().equals(actingTask.getId())) pt.setComment(comment);
                pt.setDoneAt(LocalDateTime.now());
                taskRepo.save(pt);
            }
        }
    }

    private void afterAction(Document doc) {
        long running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(doc.getProcessInstanceId()).count();
        if (running == 0) {
            finalizeArchive(doc);
        } else {
            syncTasks(doc);
        }
    }

    /** 办结归档：确保文号与套红 PDF 齐备，状态置为已归档 */
    private void finalizeArchive(Document doc) {
        DocTemplate template = templateRepo.findById(doc.getTemplateId())
                .orElseThrow(() -> new ApiException("模板不存在"));
        if (doc.getDocNo() == null) {
            doc.setDocNo(numberService.nextNumber(template));
        }
        if (doc.getPdfKey() == null) {
            byte[] pdf = redHeadPdfService.generate(doc, template);
            String key = "doc/" + doc.getId() + "/pdf/redhead-a" + doc.getAttempt() + ".pdf";
            storage.put(key, new ByteArrayInputStream(pdf), pdf.length);
            doc.setPdfKey(key);
        }
        LocalDateTime now = LocalDateTime.now();
        doc.setStatus(Document.STATUS_ARCHIVED);
        doc.setIssuedAt(now);
        doc.setArchivedAt(now);
        doc.setCurrentNodeKey(null);
        doc.setCurrentNodeName(null);
        doc.setUpdatedAt(now);
        documentRepo.save(doc);
        trace(doc, null, "ARCHIVE", null, null, "办结归档，发文字号 " + doc.getDocNo());
        notificationService.send(doc.getCreatedBy(), Notification.TYPE_ARCHIVE, "公文已办结归档",
                "《" + doc.getTitle() + "》已签发归档，文号 " + doc.getDocNo(), doc.getId());
    }

    // ==================== 催办 ====================

    @Transactional
    public void urge(Long docId, UserEntity user) {
        Document doc = getDoc(docId);
        requireRunning(doc);
        if (!doc.getCreatedBy().equals(user.getId()) && !Boolean.TRUE.equals(user.getAdmin())) {
            throw ApiException.forbidden("仅拟稿人或管理员可催办");
        }
        List<DocTask> pending = taskRepo.findByDocIdAndAttemptAndStatus(
                docId, doc.getAttempt(), DocTask.STATUS_PENDING);
        for (DocTask pt : pending) {
            notificationService.send(pt.getAssigneeId(), Notification.TYPE_URGE, "催办提醒",
                    "《" + doc.getTitle() + "》请尽快办理（" + pt.getNodeName() + "）", docId);
        }
        trace(doc, user, "URGE", doc.getCurrentNodeName(), null, "催办 " + pending.size() + " 位办理人");
    }

    // ==================== 签章 ====================

    @Transactional
    public SealRecord seal(Long docId, Long sealId, String type, Integer pageNo,
                           Float x, Float y, Float width, UserEntity user) {
        Document doc = getDoc(docId);
        requireRunning(doc);
        boolean myTurn = taskRepo.findByDocIdAndAttemptAndStatus(docId, doc.getAttempt(), DocTask.STATUS_PENDING)
                .stream().anyMatch(t -> t.getAssigneeId().equals(user.getId()));
        if (!myTurn && !Boolean.TRUE.equals(user.getAdmin())) {
            throw ApiException.forbidden("仅当前办理人可盖章");
        }
        Seal seal = sealService.requireUsable(sealId, user);
        String sealType = SealRecord.TYPE_STITCH.equals(type) ? SealRecord.TYPE_STITCH : SealRecord.TYPE_LOCATE;
        SealRecord record = pdfSealService.stamp(doc, seal, sealType, pageNo, x, y, width, user);
        doc.setPdfKey(record.getPdfKey());
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepo.save(doc);
        trace(doc, user, "SEAL", doc.getCurrentNodeName(), null,
                "盖章：" + seal.getName() + (SealRecord.TYPE_STITCH.equals(sealType) ? "（骑缝章）" : "（定位章）"));
        return record;
    }

    /** 验章：逐条核对盖章记录中 PDF 的 SHA-256，并校验当前 PDF 是否等于最后一次盖章版本 */
    public Map<String, Object> sealVerify(Long docId, UserEntity user) {
        Document doc = getDoc(docId);
        permissionService.requireView(doc, user);
        List<SealRecord> records = sealRecordRepo.findByDocIdOrderById(docId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (SealRecord r : records) {
            boolean valid = storage.exists(r.getPdfKey())
                    && PdfSealService.sha256Hex(storage.getBytes(r.getPdfKey())).equals(r.getPdfHash());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("sealName", r.getSealName());
            item.put("type", r.getType());
            item.put("pageNo", r.getPageNo());
            item.put("operatorName", r.getOperatorName());
            item.put("createdAt", r.getCreatedAt());
            item.put("pdfHash", r.getPdfHash());
            item.put("valid", valid);
            items.add(item);
        }
        String currentHash = null;
        if (doc.getPdfKey() != null && storage.exists(doc.getPdfKey())) {
            currentHash = PdfSealService.sha256Hex(storage.getBytes(doc.getPdfKey()));
        }
        boolean currentValid = records.isEmpty()
                ? currentHash != null
                : records.get(records.size() - 1).getPdfHash().equals(currentHash);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", items);
        result.put("currentHash", currentHash);
        result.put("currentValid", currentValid);
        return result;
    }

    // ==================== 查询 ====================

    public Document getDoc(Long id) {
        return documentRepo.findById(id).orElseThrow(() -> ApiException.notFound("公文"));
    }

    public List<FlowNodeCfg> snapshotNodes(Document doc) {
        if (doc.getFlowSnapshotJson() == null) return List.of();
        return Jsons.read(doc.getFlowSnapshotJson(), new TypeReference<List<FlowNodeCfg>>() {});
    }

    private void requireRunning(Document doc) {
        if (!Document.STATUS_RUNNING.equals(doc.getStatus())) {
            throw new ApiException("公文不在流转中");
        }
    }

    private DocTask requireMyPendingTask(Long taskId, Long docId, UserEntity user) {
        DocTask t = taskRepo.findById(taskId).orElseThrow(() -> ApiException.notFound("任务"));
        if (!t.getDocId().equals(docId)) throw new ApiException("任务与公文不匹配");
        if (!DocTask.STATUS_PENDING.equals(t.getStatus())) throw new ApiException("任务已办理");
        if (!t.getAssigneeId().equals(user.getId())) {
            throw ApiException.forbidden("仅当前办理人可办理该任务");
        }
        return t;
    }

    private int indexOfNode(List<FlowNodeCfg> nodes, String key) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getKey().equals(key)) return i;
        }
        return -1;
    }

    private List<String> findActivityInstanceIds(String processInstanceId, String nodeKey) {
        ActivityInstance root = runtimeService.getActivityInstance(processInstanceId);
        List<ActivityInstance> found = new ArrayList<>();
        collectActivityInstances(root, nodeKey, found);
        // 多实例：取消 miBody 即取消全部并行/串行实例
        List<String> body = found.stream()
                .filter(a -> (nodeKey + "#multiInstanceBody").equals(a.getActivityId()))
                .map(ActivityInstance::getId)
                .toList();
        if (!body.isEmpty()) return body;
        return found.stream()
                .filter(a -> nodeKey.equals(a.getActivityId()))
                .map(ActivityInstance::getId)
                .toList();
    }

    private void collectActivityInstances(ActivityInstance ai, String nodeKey, List<ActivityInstance> out) {
        if (ai.getActivityId() != null
                && (ai.getActivityId().equals(nodeKey) || ai.getActivityId().equals(nodeKey + "#multiInstanceBody"))) {
            out.add(ai);
        }
        for (ActivityInstance child : ai.getChildActivityInstances()) {
            collectActivityInstances(child, nodeKey, out);
        }
    }

    public void trace(Document doc, UserEntity actor, String action, String nodeName, String comment, String detail) {
        DocTrace trace = new DocTrace();
        trace.setDocId(doc.getId());
        trace.setActorId(actor == null ? 0L : actor.getId());
        trace.setActorName(actor == null ? "系统" : actor.getName());
        trace.setAction(action);
        trace.setNodeName(nodeName);
        trace.setComment(comment);
        trace.setDetail(detail);
        traceRepo.save(trace);
    }
}
