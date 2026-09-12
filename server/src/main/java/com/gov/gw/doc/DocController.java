package com.gov.gw.doc;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiResponse;
import com.gov.gw.flow.FlowNodeCfg;
import com.gov.gw.flow.FlowService;
import com.gov.gw.org.UserEntity;
import com.gov.gw.seal.SealRecord;
import com.gov.gw.storage.StorageService;
import com.gov.gw.template.DocTemplate;
import com.gov.gw.template.TemplateRepo;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/docs")
public class DocController {
    private final DocService docService;
    private final DocumentRepo documentRepo;
    private final DocTaskRepo taskRepo;
    private final DocTraceRepo traceRepo;
    private final TemplateRepo templateRepo;
    private final PermissionService permissionService;
    private final StorageService storage;
    private final AuthService authService;

    public DocController(DocService docService, DocumentRepo documentRepo, DocTaskRepo taskRepo,
                         DocTraceRepo traceRepo, TemplateRepo templateRepo,
                         PermissionService permissionService, StorageService storage,
                         AuthService authService) {
        this.docService = docService;
        this.documentRepo = documentRepo;
        this.taskRepo = taskRepo;
        this.traceRepo = traceRepo;
        this.templateRepo = templateRepo;
        this.permissionService = permissionService;
        this.storage = storage;
        this.authService = authService;
    }

    // ==================== 拟稿 ====================

    @PostMapping
    public ApiResponse<Document> create(@RequestBody DocService.DocReq req) {
        return ApiResponse.ok(docService.createDraft(req, authService.current()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Document> update(@PathVariable Long id, @RequestBody DocService.DocReq req) {
        return ApiResponse.ok(docService.updateDraft(id, req, authService.current()));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Document> submit(@PathVariable Long id) {
        return ApiResponse.ok(docService.submit(id, authService.current()));
    }

    // ==================== 办理 ====================

    public record CompleteReq(@NotNull Long taskId, String comment) {}

    @PostMapping("/{id}/complete")
    public ApiResponse<Document> complete(@PathVariable Long id, @RequestBody CompleteReq req) {
        return ApiResponse.ok(docService.complete(id, req.taskId(), req.comment(), authService.current()));
    }

    @PostMapping("/{id}/return-prev")
    public ApiResponse<Document> returnPrev(@PathVariable Long id, @RequestBody CompleteReq req) {
        return ApiResponse.ok(docService.returnPrev(id, req.taskId(), req.comment(), authService.current()));
    }

    @PostMapping("/{id}/return-draft")
    public ApiResponse<Document> returnDraft(@PathVariable Long id, @RequestBody CompleteReq req) {
        return ApiResponse.ok(docService.returnDraft(id, req.taskId(), req.comment(), authService.current()));
    }

    @PostMapping("/{id}/urge")
    public ApiResponse<Void> urge(@PathVariable Long id) {
        docService.urge(id, authService.current());
        return ApiResponse.ok();
    }

    // ==================== 签章 ====================

    public record SealReq(@NotNull Long sealId, String type, Integer pageNo,
                          Float x, Float y, Float width) {}

    @PostMapping("/{id}/seal")
    public ApiResponse<SealRecord> seal(@PathVariable Long id, @RequestBody SealReq req) {
        return ApiResponse.ok(docService.seal(id, req.sealId(), req.type(), req.pageNo(),
                req.x(), req.y(), req.width(), authService.current()));
    }

    @GetMapping("/{id}/verify")
    public ApiResponse<Map<String, Object>> verify(@PathVariable Long id) {
        return ApiResponse.ok(docService.sealVerify(id, authService.current()));
    }

    // ==================== 详情 / 列表 ====================

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        UserEntity me = authService.current();
        Document doc = docService.getDoc(id);
        permissionService.requireView(doc, me);

        List<DocTask> tasks = taskRepo.findByDocIdOrderById(id);
        List<DocTrace> traces = traceRepo.findByDocIdOrderById(id);
        List<FlowNodeCfg> nodes = docService.snapshotNodes(doc);
        DocTemplate template = templateRepo.findById(doc.getTemplateId()).orElse(null);

        Long myPendingTaskId = tasks.stream()
                .filter(t -> DocTask.STATUS_PENDING.equals(t.getStatus()))
                .filter(t -> t.getAssigneeId().equals(me.getId()))
                .filter(t -> Objects.equals(t.getAttempt(), doc.getAttempt()))
                .map(DocTask::getId).findFirst().orElse(null);
        boolean currentIsIssue = nodes.stream()
                .anyMatch(n -> n.getKey().equals(doc.getCurrentNodeKey()) && FlowService.TYPE_ISSUE.equals(n.getType()));
        boolean creator = doc.getCreatedBy().equals(me.getId());

        Map<String, Object> perms = new LinkedHashMap<>();
        perms.put("canEdit", creator && (Document.STATUS_DRAFT.equals(doc.getStatus()) || Document.STATUS_RETURNED.equals(doc.getStatus())));
        perms.put("canSubmit", creator && (Document.STATUS_DRAFT.equals(doc.getStatus()) || Document.STATUS_RETURNED.equals(doc.getStatus())));
        perms.put("myPendingTaskId", myPendingTaskId);
        perms.put("canUrge", Document.STATUS_RUNNING.equals(doc.getStatus()) && (creator || Boolean.TRUE.equals(me.getAdmin())));
        perms.put("canSeal", Document.STATUS_RUNNING.equals(doc.getStatus()) && myPendingTaskId != null && currentIsIssue);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("doc", doc);
        data.put("templateName", template == null ? "" : template.getName());
        data.put("flowNodes", nodes);
        data.put("tasks", enrichTasks(tasks));
        data.put("traces", traces);
        data.put("perms", perms);
        data.put("hasPdf", doc.getPdfKey() != null);
        return ApiResponse.ok(data);
    }

    /** 待办 */
    @GetMapping("/tasks/todo")
    public ApiResponse<List<Map<String, Object>>> todo() {
        UserEntity me = authService.current();
        List<DocTask> tasks = taskRepo.findByAssigneeIdAndStatusOrderByIdDesc(me.getId(), DocTask.STATUS_PENDING);
        return ApiResponse.ok(enrichTasks(tasks));
    }

    /** 已办 */
    @GetMapping("/tasks/done")
    public ApiResponse<List<Map<String, Object>>> done() {
        UserEntity me = authService.current();
        List<DocTask> tasks = taskRepo.findByAssigneeIdAndStatusInOrderByIdDesc(me.getId(),
                List.of(DocTask.STATUS_DONE, DocTask.STATUS_RETURNED));
        return ApiResponse.ok(enrichTasks(tasks));
    }

    /** 我拟的稿 */
    @GetMapping("/mine")
    public ApiResponse<List<Document>> mine() {
        return ApiResponse.ok(documentRepo.findByCreatedByOrderByUpdatedAtDesc(authService.currentId()));
    }

    /** 在办/公文查询（按权限过滤） */
    @GetMapping("/search")
    public ApiResponse<List<Document>> search(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long templateId) {
        UserEntity me = authService.current();
        Specification<Document> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                ps.add(cb.or(cb.like(root.get("title"), "%" + keyword + "%"),
                        cb.like(root.get("docNo"), "%" + keyword + "%")));
            }
            if (templateId != null) ps.add(cb.equal(root.get("templateId"), templateId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<Document> result = documentRepo.findAll(spec).stream()
                .filter(d -> permissionService.canView(d, me))
                .sorted(Comparator.comparing(Document::getUpdatedAt).reversed())
                .limit(200)
                .toList();
        return ApiResponse.ok(result);
    }

    /** 归档查询 */
    @GetMapping("/archives")
    public ApiResponse<List<Document>> archives(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String docNo,
                                                @RequestParam(required = false) Long templateId,
                                                @RequestParam(required = false) Integer secretLevel,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        UserEntity me = authService.current();
        Specification<Document> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), Document.STATUS_ARCHIVED));
            if (keyword != null && !keyword.isBlank()) ps.add(cb.like(root.get("title"), "%" + keyword + "%"));
            if (docNo != null && !docNo.isBlank()) ps.add(cb.like(root.get("docNo"), "%" + docNo + "%"));
            if (templateId != null) ps.add(cb.equal(root.get("templateId"), templateId));
            if (secretLevel != null) ps.add(cb.equal(root.get("secretLevel"), secretLevel));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            if (from != null && !from.isBlank()) ps.add(cb.greaterThanOrEqualTo(root.get("archivedAt"), LocalDateTime.parse(from + " 00:00:00", fmt)));
            if (to != null && !to.isBlank()) ps.add(cb.lessThanOrEqualTo(root.get("archivedAt"), LocalDateTime.parse(to + " 23:59:59", fmt)));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<Document> result = documentRepo.findAll(spec).stream()
                .filter(d -> permissionService.canView(d, me))
                .sorted(Comparator.comparing(Document::getArchivedAt).reversed())
                .limit(200)
                .toList();
        return ApiResponse.ok(result);
    }

    /** 套红/盖章后 PDF 下载 */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<InputStreamResource> pdf(@PathVariable Long id) {
        UserEntity me = authService.current();
        Document doc = docService.getDoc(id);
        permissionService.requireView(doc, me);
        if (doc.getPdfKey() == null) {
            throw com.gov.gw.common.ApiException.notFound("PDF（流程到达签发节点后生成）");
        }
        String filename = (doc.getDocNo() == null ? "公文-" + id : doc.getDocNo()) + ".pdf";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .body(new InputStreamResource(storage.get(doc.getPdfKey())));
    }

    // ==================== 组装 ====================

    private List<Map<String, Object>> enrichTasks(List<DocTask> tasks) {
        Set<Long> docIds = tasks.stream().map(DocTask::getDocId).collect(Collectors.toSet());
        Map<Long, Document> docs = documentRepo.findAllById(docIds).stream()
                .collect(Collectors.toMap(Document::getId, d -> d));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DocTask t : tasks) {
            Document doc = docs.get(t.getDocId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("docId", t.getDocId());
            m.put("nodeName", t.getNodeName());
            m.put("status", t.getStatus());
            m.put("action", t.getAction());
            m.put("comment", t.getComment());
            m.put("createdAt", t.getCreatedAt());
            m.put("doneAt", t.getDoneAt());
            m.put("attempt", t.getAttempt());
            if (doc != null) {
                m.put("title", doc.getTitle());
                m.put("docNo", doc.getDocNo());
                m.put("docStatus", doc.getStatus());
                m.put("secretLevel", doc.getSecretLevel());
                m.put("overdue", DocTask.STATUS_PENDING.equals(t.getStatus()) && isOverdue(doc, t));
            }
            result.add(m);
        }
        return result;
    }

    private boolean isOverdue(Document doc, DocTask task) {
        return docService.snapshotNodes(doc).stream()
                .filter(n -> n.getKey().equals(task.getNodeKey()))
                .findFirst()
                .map(n -> task.getCreatedAt().plusHours(n.getTimeoutHours()).isBefore(LocalDateTime.now()))
                .orElse(false);
    }
}
