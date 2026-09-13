package com.gov.gw.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gov.gw.common.Jsons;
import com.gov.gw.doc.*;
import com.gov.gw.flow.FlowNodeCfg;
import com.gov.gw.notify.NotificationService;
import com.gov.gw.org.OrgEntity;
import com.gov.gw.org.OrgRepo;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import com.gov.gw.template.DocTemplate;
import com.gov.gw.template.TemplateRepo;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 效能分析服务：定时从 Camunda 历史表（ACT_HI_*，经 HistoryService 读取）与业务库抽取数据，
 * 刷新 doc_analysis 分析表，并基于分析结果生成督办单（reminder_log）。
 * 只读取业务数据，不回写业务表。
 */
@Service
public class AnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /** 退回原因分类（关键词命中即归入，按声明顺序优先） */
    static final Map<String, List<String>> RETURN_CATEGORIES = new LinkedHashMap<>();
    static {
        RETURN_CATEGORIES.put("格式规范", List.of("格式", "字体", "字号", "排版", "版式", "红头", "行距", "页边距", "页码"));
        RETURN_CATEGORIES.put("附件材料", List.of("附件", "材料", "缺少", "遗漏", "补正", "不全"));
        RETURN_CATEGORIES.put("内容质量", List.of("内容", "数据", "事实", "依据", "表述", "错别字", "文字", "政策"));
        RETURN_CATEGORIES.put("行文规范", List.of("标题", "文种", "主送", "抄送", "文号", "密级", "主题词"));
        RETURN_CATEGORIES.put("程序合规", List.of("程序", "会签", "征求意见", "合法性", "审核"));
    }
    static final String CATEGORY_OTHER = "其他";

    private final DocumentRepo documentRepo;
    private final DocTaskRepo taskRepo;
    private final DocTraceRepo traceRepo;
    private final DocAnalysisRepo analysisRepo;
    private final ReminderLogRepo reminderLogRepo;
    private final UserRepo userRepo;
    private final OrgRepo orgRepo;
    private final TemplateRepo templateRepo;
    private final HistoryService historyService;
    private final NotificationService notificationService;

    public AnalysisService(DocumentRepo documentRepo, DocTaskRepo taskRepo, DocTraceRepo traceRepo,
                           DocAnalysisRepo analysisRepo, ReminderLogRepo reminderLogRepo,
                           UserRepo userRepo, OrgRepo orgRepo, TemplateRepo templateRepo,
                           HistoryService historyService, NotificationService notificationService) {
        this.documentRepo = documentRepo;
        this.taskRepo = taskRepo;
        this.traceRepo = traceRepo;
        this.analysisRepo = analysisRepo;
        this.reminderLogRepo = reminderLogRepo;
        this.userRepo = userRepo;
        this.orgRepo = orgRepo;
        this.templateRepo = templateRepo;
        this.historyService = historyService;
        this.notificationService = notificationService;
    }

    // ==================== 数据抽取 ====================

    /** 全量刷新分析表（幂等，按 docId 覆盖）。返回抽取公文数 */
    @Transactional
    public int extract() {
        Map<Long, UserEntity> users = userRepo.findAll().stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));
        Map<Long, OrgEntity> orgs = orgRepo.findAll().stream()
                .collect(Collectors.toMap(OrgEntity::getId, o -> o));
        Map<Long, DocTemplate> templates = templateRepo.findAll().stream()
                .collect(Collectors.toMap(DocTemplate::getId, t -> t));
        int count = 0;
        for (Document doc : documentRepo.findAll()) {
            if (doc.getAttempt() == null || doc.getAttempt() == 0) continue; // 从未提交不统计
            try {
                analysisRepo.save(buildAnalysis(doc, users, orgs, templates));
                count++;
            } catch (Exception e) {
                log.warn("抽取公文 {} 分析数据失败：{}", doc.getId(), e.getMessage());
            }
        }
        return count;
    }

    private DocAnalysis buildAnalysis(Document doc, Map<Long, UserEntity> users,
                                      Map<Long, OrgEntity> orgs, Map<Long, DocTemplate> templates) {
        List<DocTrace> traces = traceRepo.findByDocIdOrderById(doc.getId());
        List<DocTask> tasks = taskRepo.findByDocIdOrderById(doc.getId());
        Map<String, FlowNodeCfg> nodeByKey = parseSnapshot(doc);

        DocAnalysis a = analysisRepo.findByDocId(doc.getId()).orElse(new DocAnalysis());
        a.setDocId(doc.getId());
        a.setTitle(doc.getTitle());
        a.setDocNo(doc.getDocNo());
        a.setTemplateId(doc.getTemplateId());
        DocTemplate template = templates.get(doc.getTemplateId());
        a.setTemplateName(template != null ? template.getName() : "未知模板");
        a.setStatus(doc.getStatus());
        a.setAttempt(doc.getAttempt());

        UserEntity creator = users.get(doc.getCreatedBy());
        a.setCreatorId(doc.getCreatedBy());
        a.setCreatorName(creator != null ? creator.getName() : "未知");
        OrgEntity org = creator != null ? orgs.get(creator.getOrgId()) : null;
        a.setOrgId(org != null ? org.getId() : null);
        a.setOrgName(org != null ? org.getName() : "未知部门");
        a.setCreatorPost(creator != null ? firstPost(creator.getPosts()) : "");

        // 拟稿时长：创建 → 首次提交
        LocalDateTime firstSubmit = traces.stream()
                .filter(t -> "SUBMIT".equals(t.getAction()))
                .map(DocTrace::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(doc.getSubmittedAt());
        a.setSubmittedAt(firstSubmit);
        a.setDraftHours(firstSubmit != null ? hoursBetween(doc.getCreatedAt(), firstSubmit) : null);
        a.setArchivedAt(doc.getArchivedAt());
        a.setTotalHours(doc.getArchivedAt() != null && firstSubmit != null
                ? hoursBetween(firstSubmit, doc.getArchivedAt()) : null);

        // 节点任务明细：优先 Camunda 历史表（ACT_HI_TASKINST），历史缺失时回退业务任务表
        List<NodeStat> stats = nodeStatsFromHistory(doc, nodeByKey, users, orgs, tasks);
        if (stats.isEmpty()) {
            stats = nodeStatsFromBiz(tasks, nodeByKey, users, orgs);
        }
        a.setNodeStatsJson(Jsons.write(stats));

        // 会签完成周期：会签节点最早任务开始 → 最晚任务完成（全部完成才统计）
        List<NodeStat> countersign = stats.stream()
                .filter(s -> "COUNTERSIGN".equals(s.type()) && s.start() != null)
                .toList();
        boolean countersignDone = !countersign.isEmpty() && countersign.stream().allMatch(s -> s.end() != null);
        a.setCountersignHours(countersignDone
                ? hoursBetween(countersign.stream().map(NodeStat::start).min(Comparator.naturalOrder()).get(),
                               countersign.stream().map(NodeStat::end).max(Comparator.naturalOrder()).get())
                : null);

        // 退回情况
        List<DocTrace> returns = traces.stream()
                .filter(t -> "RETURN_PREV".equals(t.getAction()) || "RETURN_DRAFT".equals(t.getAction()))
                .toList();
        a.setReturnCount(returns.size());
        a.setReturnReasonsJson(Jsons.write(returns.stream()
                .filter(t -> t.getComment() != null && !t.getComment().isBlank())
                .map(t -> new ReturnReason(t.getNodeName(), t.getActorName(), t.getComment(), t.getCreatedAt()))
                .toList()));

        // 催办次数
        a.setUrgeCount((int) traces.stream().filter(t -> "URGE".equals(t.getAction())).count());

        a.setOverdueDays(computeOverdueDays(doc, tasks, nodeByKey));
        a.setExtractedAt(LocalDateTime.now());
        return a;
    }

    /** 从 ACT_HI_PROCINST / ACT_HI_TASKINST 抽取节点任务明细（按 businessKey=公文ID 定位全部流程实例） */
    private List<NodeStat> nodeStatsFromHistory(Document doc, Map<String, FlowNodeCfg> nodeByKey,
                                                Map<Long, UserEntity> users, Map<Long, OrgEntity> orgs,
                                                List<DocTask> bizTasks) {
        Map<String, DocTask> byCamundaId = bizTasks.stream()
                .collect(Collectors.toMap(DocTask::getCamundaTaskId, t -> t, (x, y) -> x));
        List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(String.valueOf(doc.getId())).list();
        List<NodeStat> stats = new ArrayList<>();
        for (HistoricProcessInstance hpi : instances) {
            for (HistoricTaskInstance hti : historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(hpi.getProcessInstanceId()).list()) {
                if (hti.getTaskDefinitionKey() == null || hti.getStartTime() == null) continue;
                DocTask bt = byCamundaId.get(hti.getId());
                Long assigneeId = bt != null ? bt.getAssigneeId() : parseLong(hti.getAssignee());
                FlowNodeCfg cfg = nodeByKey.get(hti.getTaskDefinitionKey());
                LocalDateTime start = toLocal(hti.getStartTime());
                LocalDateTime end = hti.getEndTime() != null ? toLocal(hti.getEndTime()) : null;
                String action = bt != null ? bt.getAction() : null;
                if (action == null && bt != null && DocTask.STATUS_CANCELED.equals(bt.getStatus())) {
                    action = DocTask.STATUS_CANCELED;
                }
                stats.add(toNodeStat(hti.getTaskDefinitionKey(), cfg, assigneeId, start, end, action, users, orgs));
            }
        }
        stats.sort(Comparator.comparing(NodeStat::start, Comparator.nullsLast(Comparator.naturalOrder())));
        return stats;
    }

    /** 历史表无数据时（如超出 historyTimeToLive）回退到业务任务表 */
    private List<NodeStat> nodeStatsFromBiz(List<DocTask> tasks, Map<String, FlowNodeCfg> nodeByKey,
                                            Map<Long, UserEntity> users, Map<Long, OrgEntity> orgs) {
        List<NodeStat> stats = new ArrayList<>();
        for (DocTask t : tasks) {
            String action = t.getAction() != null ? t.getAction()
                    : DocTask.STATUS_CANCELED.equals(t.getStatus()) ? DocTask.STATUS_CANCELED : null;
            stats.add(toNodeStat(t.getNodeKey(), nodeByKey.get(t.getNodeKey()), t.getAssigneeId(),
                    t.getCreatedAt(), t.getDoneAt(), action, users, orgs));
        }
        return stats;
    }

    private NodeStat toNodeStat(String key, FlowNodeCfg cfg, Long assigneeId,
                                LocalDateTime start, LocalDateTime end, String action,
                                Map<Long, UserEntity> users, Map<Long, OrgEntity> orgs) {
        UserEntity u = assigneeId != null ? users.get(assigneeId) : null;
        OrgEntity o = u != null ? orgs.get(u.getOrgId()) : null;
        return new NodeStat(key,
                cfg != null ? cfg.getName() : key,
                cfg != null ? cfg.getType() : "",
                assigneeId,
                u != null ? u.getName() : (assigneeId != null ? "用户" + assigneeId : ""),
                o != null ? o.getName() : "",
                u != null ? firstPost(u.getPosts()) : "",
                start, end,
                end != null ? hoursBetween(start, end) : null,
                action);
    }

    /** 当前超时天数：在办件当前待办任务超过节点时限的最大天数（未超时为 0） */
    private int computeOverdueDays(Document doc, List<DocTask> tasks, Map<String, FlowNodeCfg> nodeByKey) {
        if (!Document.STATUS_RUNNING.equals(doc.getStatus())) return 0;
        int max = 0;
        LocalDateTime now = LocalDateTime.now();
        for (DocTask t : tasks) {
            if (!DocTask.STATUS_PENDING.equals(t.getStatus())) continue;
            if (!Objects.equals(t.getAttempt(), doc.getAttempt())) continue;
            int timeoutHours = Optional.ofNullable(nodeByKey.get(t.getNodeKey()))
                    .map(FlowNodeCfg::getTimeoutHours).orElse(24);
            long overdueHours = Duration.between(t.getCreatedAt().plusHours(timeoutHours), now).toHours();
            if (overdueHours > 0) max = Math.max(max, (int) (overdueHours / 24) + 1);
        }
        return max;
    }

    // ==================== 督办单 ====================

    /** 对超时未办结、催办≥3次的在办公文生成督办单并推送分管领导（同一公文仅一张未处理督办单） */
    @Transactional
    public int generateReminders() {
        int created = 0;
        for (DocAnalysis a : analysisRepo.findAll()) {
            if (!Document.STATUS_RUNNING.equals(a.getStatus())) continue;
            List<String> reasons = new ArrayList<>();
            if (a.getOverdueDays() != null && a.getOverdueDays() >= 1) {
                reasons.add("超时未办结 " + a.getOverdueDays() + " 天");
            }
            if (a.getUrgeCount() != null && a.getUrgeCount() >= 3) {
                reasons.add("催办 " + a.getUrgeCount() + " 次");
            }
            if (reasons.isEmpty()) continue;
            if (reminderLogRepo.existsByDocIdAndStatus(a.getDocId(), ReminderLog.STATUS_OPEN)) continue;

            Document doc = documentRepo.findById(a.getDocId()).orElse(null);
            if (doc == null || !Document.STATUS_RUNNING.equals(doc.getStatus())) continue;
            List<DocTask> pending = taskRepo.findByDocIdAndAttemptAndStatus(
                    doc.getId(), doc.getAttempt(), DocTask.STATUS_PENDING);
            String assigneeNames = pending.stream()
                    .map(t -> userName(t.getAssigneeId()))
                    .distinct().collect(Collectors.joining("、"));
            UserEntity leader = resolveLeader(doc);

            ReminderLog r = new ReminderLog();
            r.setDocId(doc.getId());
            r.setDocNo(doc.getDocNo());
            r.setTitle(doc.getTitle());
            r.setOrgName(a.getOrgName());
            r.setReason(String.join("；", reasons));
            r.setOverdueDays(a.getOverdueDays());
            r.setUrgeCount(a.getUrgeCount());
            r.setCurrentNodeName(doc.getCurrentNodeName());
            r.setAssigneeNames(assigneeNames);
            if (leader != null) {
                r.setLeaderId(leader.getId());
                r.setLeaderName(leader.getName());
            }
            reminderLogRepo.save(r);

            if (leader != null) {
                notificationService.send(leader.getId(), Notification.TYPE_SUPERVISE, "督办单",
                        "《" + doc.getTitle() + "》" + (doc.getDocNo() != null ? "（" + doc.getDocNo() + "）" : "")
                                + String.join("，", reasons) + "，当前节点：" + doc.getCurrentNodeName()
                                + "，责任人：" + assigneeNames + "，请督促办理",
                        doc.getId());
            }
            created++;
            log.info("生成督办单 docId={} 原因={}", doc.getId(), r.getReason());
        }
        return created;
    }

    /** 分管领导：拟稿人所在部门负责人，缺省回退到系统管理员 */
    private UserEntity resolveLeader(Document doc) {
        UserEntity creator = userRepo.findById(doc.getCreatedBy()).orElse(null);
        if (creator != null) {
            OrgEntity org = orgRepo.findById(creator.getOrgId()).orElse(null);
            if (org != null && org.getLeaderId() != null) {
                UserEntity leader = userRepo.findById(org.getLeaderId()).orElse(null);
                if (leader != null && Boolean.TRUE.equals(leader.getEnabled())) return leader;
            }
        }
        return userRepo.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getAdmin()) && Boolean.TRUE.equals(u.getEnabled()))
                .findFirst().orElse(null);
    }

    // ==================== 统计查询 ====================

    /** 总览卡片 */
    public Map<String, Object> overview() {
        List<DocAnalysis> all = analysisRepo.findAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("archived", all.stream().filter(a -> Document.STATUS_ARCHIVED.equals(a.getStatus())).count());
        m.put("running", all.stream().filter(a -> Document.STATUS_RUNNING.equals(a.getStatus())).count());
        m.put("returned", all.stream().filter(a -> Document.STATUS_RETURNED.equals(a.getStatus())).count());
        m.put("avgTotalHours", avg(all.stream().map(DocAnalysis::getTotalHours).filter(Objects::nonNull).toList()));
        m.put("avgDraftHours", avg(all.stream().map(DocAnalysis::getDraftHours).filter(Objects::nonNull).toList()));
        long withReturn = all.stream().filter(a -> a.getReturnCount() != null && a.getReturnCount() > 0).count();
        m.put("returnRate", all.isEmpty() ? 0 : round1(100.0 * withReturn / all.size()));
        m.put("openReminders", reminderLogRepo.findByStatusOrderByIdDesc(ReminderLog.STATUS_OPEN).size());
        return m;
    }

    /** 办理时效统计：dim = org | post | template，按办结件统计平均时长 */
    public List<Map<String, Object>> efficiency(String dim) {
        List<DocAnalysis> archived = analysisRepo.findAll().stream()
                .filter(a -> a.getTotalHours() != null)
                .toList();
        Map<String, List<DocAnalysis>> groups = archived.stream()
                .collect(Collectors.groupingBy(a -> dimKey(a, dim), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<DocAnalysis> g = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", e.getKey());
            row.put("count", g.size());
            row.put("avgDraftHours", avg(g.stream().map(DocAnalysis::getDraftHours).filter(Objects::nonNull).toList()));
            row.put("avgHandleHours", avg(g.stream().map(this::handleHours).filter(Objects::nonNull).toList()));
            row.put("avgCountersignHours", avg(g.stream().map(DocAnalysis::getCountersignHours).filter(Objects::nonNull).toList()));
            row.put("avgTotalHours", avg(g.stream().map(DocAnalysis::getTotalHours).filter(Objects::nonNull).toList()));
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare((int) b.get("count"), (int) a.get("count")));
        return rows;
    }

    /** 办结趋势：近 months 个月逐月办结量与平均时长 */
    public List<Map<String, Object>> trend(int months) {
        List<DocAnalysis> all = analysisRepo.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            List<DocAnalysis> inMonth = all.stream()
                    .filter(a -> a.getArchivedAt() != null && YearMonth.from(a.getArchivedAt()).equals(ym))
                    .toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym.toString());
            row.put("count", inMonth.size());
            row.put("avgTotalHours", avg(inMonth.stream().map(DocAnalysis::getTotalHours).filter(Objects::nonNull).toList()));
            row.put("avgDraftHours", avg(inMonth.stream().map(DocAnalysis::getDraftHours).filter(Objects::nonNull).toList()));
            rows.add(row);
        }
        return rows;
    }

    /** 各节点停留时长（全部已提交公文的已办任务） */
    public List<Map<String, Object>> nodeDurations() {
        Map<String, List<Double>> byNode = new LinkedHashMap<>();
        for (DocAnalysis a : analysisRepo.findAll()) {
            for (NodeStat s : parseNodeStats(a)) {
                if (s.hours() == null || DocTask.STATUS_CANCELED.equals(s.action())) continue;
                byNode.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(s.hours());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var e : byNode.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", e.getKey());
            row.put("count", e.getValue().size());
            row.put("avgHours", avg(e.getValue()));
            row.put("maxHours", round1(e.getValue().stream().mapToDouble(Double::doubleValue).max().orElse(0)));
            rows.add(row);
        }
        rows.sort((a, b) -> Double.compare((double) b.get("avgHours"), (double) a.get("avgHours")));
        return rows;
    }

    /** 退回热点：原因分类占比 + 关键词词云 + 最近退回意见 */
    public Map<String, Object> returnAnalysis() {
        List<Map<String, Object>> recent = new ArrayList<>();
        Map<String, Integer> categoryCount = new LinkedHashMap<>();
        RETURN_CATEGORIES.keySet().forEach(k -> categoryCount.put(k, 0));
        categoryCount.put(CATEGORY_OTHER, 0);
        Map<String, Integer> keywords = new HashMap<>();
        int total = 0;

        for (DocAnalysis a : analysisRepo.findAll()) {
            List<ReturnReason> reasons = Jsons.read(a.getReturnReasonsJson() == null ? "[]" : a.getReturnReasonsJson(),
                    new TypeReference<List<ReturnReason>>() {});
            for (ReturnReason r : reasons) {
                total++;
                String category = categorize(r.comment());
                categoryCount.merge(category, 1, Integer::sum);
                for (var e : RETURN_CATEGORIES.entrySet()) {
                    for (String kw : e.getValue()) {
                        if (r.comment().contains(kw)) keywords.merge(kw, 1, Integer::sum);
                    }
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("docId", a.getDocId());
                item.put("title", a.getTitle());
                item.put("nodeName", r.nodeName());
                item.put("actorName", r.actorName());
                item.put("comment", r.comment());
                item.put("category", category);
                item.put("at", r.at());
                recent.add(item);
            }
        }
        recent.sort((x, y) -> {
            LocalDateTime ax = (LocalDateTime) x.get("at");
            LocalDateTime ay = (LocalDateTime) y.get("at");
            return Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()).compare(ay, ax);
        });
        if (recent.size() > 20) recent = new ArrayList<>(recent.subList(0, 20));

        List<Map<String, Object>> categories = categoryCount.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue());
                    m.put("percent", total == 0 ? 0 : round1(100.0 * e.getValue() / total));
                    return m;
                })
                .sorted((a, b) -> Integer.compare((int) b.get("count"), (int) a.get("count")))
                .toList();
        List<Map<String, Object>> wordCloud = keywords.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("categories", categories);
        result.put("wordCloud", wordCloud);
        result.put("recent", recent);
        return result;
    }

    /** 效能排行：month=yyyy-MM，type=org（部门办结）| user（个人办理） */
    public List<Map<String, Object>> ranking(String month, String type) {
        YearMonth ym = YearMonth.parse(month);
        List<Map<String, Object>> rows = new ArrayList<>();
        if ("user".equals(type)) {
            Map<Long, List<Double>> hoursByUser = new HashMap<>();
            Map<Long, String[]> meta = new HashMap<>();
            for (DocAnalysis a : analysisRepo.findAll()) {
                for (NodeStat s : parseNodeStats(a)) {
                    if (s.end() == null || s.hours() == null) continue;
                    if (!"APPROVE".equals(s.action())) continue;
                    if (!YearMonth.from(s.end()).equals(ym)) continue;
                    if (s.assigneeId() == null) continue;
                    hoursByUser.computeIfAbsent(s.assigneeId(), k -> new ArrayList<>()).add(s.hours());
                    meta.putIfAbsent(s.assigneeId(), new String[]{s.assigneeName(), s.orgName()});
                }
            }
            for (var e : hoursByUser.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                String[] mt = meta.get(e.getKey());
                row.put("name", mt[0]);
                row.put("orgName", mt[1]);
                row.put("count", e.getValue().size());
                row.put("avgHours", avg(e.getValue()));
                rows.add(row);
            }
            rows.sort((a, b) -> {
                int c = Integer.compare((int) b.get("count"), (int) a.get("count"));
                return c != 0 ? c : Double.compare((double) a.get("avgHours"), (double) b.get("avgHours"));
            });
        } else {
            Map<String, List<DocAnalysis>> byOrg = analysisRepo.findAll().stream()
                    .filter(a -> a.getArchivedAt() != null && YearMonth.from(a.getArchivedAt()).equals(ym))
                    .collect(Collectors.groupingBy(DocAnalysis::getOrgName, LinkedHashMap::new, Collectors.toList()));
            for (var e : byOrg.entrySet()) {
                List<DocAnalysis> g = e.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", e.getKey());
                row.put("count", g.size());
                row.put("avgTotalHours", avg(g.stream().map(DocAnalysis::getTotalHours).filter(Objects::nonNull).toList()));
                row.put("avgDraftHours", avg(g.stream().map(DocAnalysis::getDraftHours).filter(Objects::nonNull).toList()));
                row.put("returnCount", g.stream().mapToInt(a -> a.getReturnCount() == null ? 0 : a.getReturnCount()).sum());
                rows.add(row);
            }
            rows.sort((a, b) -> {
                int c = Integer.compare((int) b.get("count"), (int) a.get("count"));
                return c != 0 ? c : Double.compare((double) a.get("avgTotalHours"), (double) b.get("avgTotalHours"));
            });
        }
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("rank", i + 1);
        return rows;
    }

    /** 效能排行 CSV 导出（带 BOM，Excel 可直接打开） */
    public String rankingCsv(String month, String type) {
        List<Map<String, Object>> rows = ranking(month, type);
        StringBuilder sb = new StringBuilder("\uFEFF");
        if ("user".equals(type)) {
            sb.append("排名,姓名,部门,办理数量,平均办理时长(小时)\n");
            for (Map<String, Object> r : rows) {
                sb.append(r.get("rank")).append(',')
                        .append(csv(r.get("name"))).append(',')
                        .append(csv(r.get("orgName"))).append(',')
                        .append(r.get("count")).append(',')
                        .append(r.get("avgHours")).append('\n');
            }
        } else {
            sb.append("排名,部门,办结数量,平均全程时长(小时),平均拟稿时长(小时),退回次数\n");
            for (Map<String, Object> r : rows) {
                sb.append(r.get("rank")).append(',')
                        .append(csv(r.get("name"))).append(',')
                        .append(r.get("count")).append(',')
                        .append(r.get("avgTotalHours")).append(',')
                        .append(r.get("avgDraftHours")).append(',')
                        .append(r.get("returnCount")).append('\n');
            }
        }
        return sb.toString();
    }

    // ==================== 督办单查询 ====================

    public List<ReminderLog> reminders(String status) {
        if (status != null && !status.isBlank()) {
            return reminderLogRepo.findByStatusOrderByIdDesc(status);
        }
        List<ReminderLog> all = reminderLogRepo.findAllByOrderByIdDesc();
        return all.size() > 200 ? all.subList(0, 200) : all;
    }

    @Transactional
    public ReminderLog handleReminder(Long id) {
        ReminderLog r = reminderLogRepo.findById(id)
                .orElseThrow(() -> com.gov.gw.common.ApiException.notFound("督办单"));
        r.setStatus(ReminderLog.STATUS_HANDLED);
        r.setHandledAt(LocalDateTime.now());
        return reminderLogRepo.save(r);
    }

    // ==================== 工具 ====================

    private Map<String, FlowNodeCfg> parseSnapshot(Document doc) {
        if (doc.getFlowSnapshotJson() == null) return Map.of();
        List<FlowNodeCfg> nodes = Jsons.read(doc.getFlowSnapshotJson(), new TypeReference<List<FlowNodeCfg>>() {});
        return nodes.stream().collect(Collectors.toMap(FlowNodeCfg::getKey, n -> n, (a, b) -> a));
    }

    private List<NodeStat> parseNodeStats(DocAnalysis a) {
        return Jsons.read(a.getNodeStatsJson() == null ? "[]" : a.getNodeStatsJson(),
                new TypeReference<List<NodeStat>>() {});
    }

    /** 单篇公文的办理耗时合计（已办任务，不含被取消的） */
    private Double handleHours(DocAnalysis a) {
        List<NodeStat> stats = parseNodeStats(a);
        double sum = 0;
        boolean any = false;
        for (NodeStat s : stats) {
            if (s.hours() == null || DocTask.STATUS_CANCELED.equals(s.action())) continue;
            sum += s.hours();
            any = true;
        }
        return any ? round1(sum) : null;
    }

    private String dimKey(DocAnalysis a, String dim) {
        return switch (dim == null ? "org" : dim) {
            case "post" -> a.getCreatorPost() == null || a.getCreatorPost().isBlank() ? "未设置岗位" : a.getCreatorPost();
            case "template" -> a.getTemplateName();
            default -> a.getOrgName();
        };
    }

    private String categorize(String comment) {
        for (var e : RETURN_CATEGORIES.entrySet()) {
            for (String kw : e.getValue()) {
                if (comment.contains(kw)) return e.getKey();
            }
        }
        return CATEGORY_OTHER;
    }

    private String userName(Long userId) {
        return userRepo.findById(userId).map(UserEntity::getName).orElse("用户" + userId);
    }

    private static String firstPost(String posts) {
        if (posts == null || posts.isBlank()) return "";
        return posts.split(",")[0].trim();
    }

    private static Double hoursBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return null;
        return round1(Duration.between(from, to).toMinutes() / 60.0);
    }

    private static double avg(List<Double> values) {
        return round1(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static LocalDateTime toLocal(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String csv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
