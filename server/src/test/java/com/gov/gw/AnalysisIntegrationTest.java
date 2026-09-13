package com.gov.gw;

import com.gov.gw.analysis.AnalysisService;
import com.gov.gw.analysis.DocAnalysis;
import com.gov.gw.analysis.DocAnalysisRepo;
import com.gov.gw.analysis.ReminderLog;
import com.gov.gw.analysis.ReminderLogRepo;
import com.gov.gw.doc.*;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import com.gov.gw.template.TemplateRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 效能分析：数据抽取（ACT_HI_* + 业务库 → doc_analysis）、时效/退回/排行统计、督办单生成与推送。
 */
@SpringBootTest
class AnalysisIntegrationTest {
    @Autowired DocService docService;
    @Autowired AnalysisService analysisService;
    @Autowired DocAnalysisRepo analysisRepo;
    @Autowired ReminderLogRepo reminderLogRepo;
    @Autowired DocumentRepo documentRepo;
    @Autowired DocTaskRepo taskRepo;
    @Autowired NotificationRepo notificationRepo;
    @Autowired UserRepo userRepo;
    @Autowired TemplateRepo templateRepo;

    private UserEntity user(String username) {
        return userRepo.findByUsername(username).orElseThrow();
    }

    private DocTask pendingOf(Long docId, Long assigneeId) {
        return taskRepo.findByDocIdOrderById(docId).stream()
                .filter(t -> DocTask.STATUS_PENDING.equals(t.getStatus()))
                .filter(t -> t.getAssigneeId().equals(assigneeId))
                .findFirst().orElseThrow(() -> new AssertionError("应有待办任务 assignee=" + assigneeId));
    }

    private Long mainTemplateId() {
        return templateRepo.findAll().stream()
                .filter(t -> t.getName().equals("市政府文件")).findFirst().orElseThrow().getId();
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractAndStatistics() {
        UserEntity zhangsan = user("zhangsan");
        UserEntity lisi = user("lisi");
        UserEntity wangwu = user("wangwu");
        UserEntity zhaoliu = user("zhaoliu");
        UserEntity qianqi = user("qianqi");

        // 拟稿 → 提交 → 审核退回拟稿（意见含高频关键词）→ 补正重报 → 全链办结
        Document doc = docService.createDraft(new DocService.DocReq(
                "关于效能分析测试的请示", mainTemplateId(), 1,
                "市发展改革委", "市政府办公室", "正文内容。", List.of()), zhangsan);
        doc = docService.submit(doc.getId(), zhangsan);
        doc = docService.returnDraft(doc.getId(), pendingOf(doc.getId(), lisi.getId()).getId(),
                "格式错误，附件缺少盖章页", lisi);
        assertEquals(Document.STATUS_RETURNED, doc.getStatus());

        doc = docService.submit(doc.getId(), zhangsan);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), lisi.getId()).getId(), "同意", lisi);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), wangwu.getId()).getId(), "同意", wangwu);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), zhaoliu.getId()).getId(), "同意", zhaoliu);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), qianqi.getId()).getId(), "同意，印发", qianqi);
        assertEquals(Document.STATUS_ARCHIVED, doc.getStatus());

        // 抽取
        int extracted = analysisService.extract();
        assertTrue(extracted >= 1);

        DocAnalysis a = analysisRepo.findByDocId(doc.getId()).orElseThrow();
        assertEquals(Document.STATUS_ARCHIVED, a.getStatus());
        assertEquals(1, a.getReturnCount());
        assertEquals("市政府办公室", a.getOrgName());
        assertEquals("科员", a.getCreatorPost());
        assertEquals("市政府文件", a.getTemplateName());
        assertNotNull(a.getDraftHours(), "拟稿时长");
        assertNotNull(a.getTotalHours(), "全程时长");
        assertNotNull(a.getCountersignHours(), "会签完成周期");
        assertTrue(a.getNodeStatsJson().contains("部门审核"), "节点明细应含节点名");
        assertTrue(a.getNodeStatsJson().contains("李四"), "节点明细应含办理人");
        assertTrue(a.getReturnReasonsJson().contains("格式错误"), "退回意见应入库");

        // 退回热点：分类与词云
        Map<String, Object> ret = analysisService.returnAnalysis();
        assertTrue(((Number) ret.get("total")).intValue() >= 1);
        List<Map<String, Object>> categories = (List<Map<String, Object>>) ret.get("categories");
        assertTrue(categories.stream().anyMatch(c -> "格式规范".equals(c.get("name"))), "应归类为格式规范");
        List<Map<String, Object>> cloud = (List<Map<String, Object>>) ret.get("wordCloud");
        assertTrue(cloud.stream().anyMatch(c -> "附件".equals(c.get("name"))), "词云应含关键词");
        List<Map<String, Object>> recent = (List<Map<String, Object>>) ret.get("recent");
        assertTrue(recent.stream().anyMatch(c -> String.valueOf(c.get("comment")).contains("格式错误")));

        // 时效统计三个维度 + 节点停留
        assertFalse(analysisService.efficiency("org").isEmpty());
        assertFalse(analysisService.efficiency("post").isEmpty());
        assertFalse(analysisService.efficiency("template").isEmpty());
        assertFalse(analysisService.nodeDurations().isEmpty());

        // 趋势：当月办结 ≥ 1
        List<Map<String, Object>> trend = analysisService.trend(1);
        assertEquals(1, trend.size());
        assertTrue(((Number) trend.get(0).get("count")).intValue() >= 1);

        // 效能排行：部门与个人
        String month = YearMonth.now().toString();
        List<Map<String, Object>> orgRank = analysisService.ranking(month, "org");
        assertTrue(orgRank.stream().anyMatch(r -> "市政府办公室".equals(r.get("name"))));
        assertEquals(1, ((Number) orgRank.get(0).get("rank")).intValue());
        List<Map<String, Object>> userRank = analysisService.ranking(month, "user");
        assertTrue(userRank.stream().anyMatch(r -> "李四".equals(r.get("name"))));
        String csv = analysisService.rankingCsv(month, "org");
        assertTrue(csv.contains("办结数量"));
        assertTrue(csv.contains("市政府办公室"));

        // 总览
        Map<String, Object> ov = analysisService.overview();
        assertTrue(((Number) ov.get("archived")).longValue() >= 1);
    }

    @Test
    void reminderGeneration() {
        UserEntity zhangsan = user("zhangsan");
        UserEntity lisi = user("lisi");

        Document doc = docService.createDraft(new DocService.DocReq(
                "关于督办单测试的通知", mainTemplateId(), 1,
                "市财政局", "市政府办公室", "正文内容。", List.of()), zhangsan);
        doc = docService.submit(doc.getId(), zhangsan);
        final Long docId = doc.getId();

        // 人为制造超时：审核节点时限 24h，将待办创建时间拨回 3 天前
        DocTask task = pendingOf(docId, lisi.getId());
        task.setCreatedAt(LocalDateTime.now().minusDays(3));
        taskRepo.save(task);

        analysisService.extract();
        DocAnalysis a = analysisRepo.findByDocId(docId).orElseThrow();
        assertTrue(a.getOverdueDays() >= 2, "超时天数应 ≥ 2，实际：" + a.getOverdueDays());

        // 生成督办单并推送分管领导（拟稿部门=市政府办公室，负责人=李四）
        analysisService.generateReminders();
        ReminderLog r = reminderLogRepo.findByStatusOrderByIdDesc(ReminderLog.STATUS_OPEN).stream()
                .filter(x -> x.getDocId().equals(docId)).findFirst()
                .orElseThrow(() -> new AssertionError("应生成督办单"));
        assertTrue(r.getReason().contains("超时未办结"), "督办原因：" + r.getReason());
        assertEquals("李四", r.getLeaderName());
        assertTrue(r.getAssigneeNames().contains("李四"));
        assertTrue(notificationRepo.findByUserIdOrderByIdDesc(lisi.getId()).stream()
                .anyMatch(n -> "SUPERVISE".equals(n.getType()) && docId.equals(n.getDocId())),
                "分管领导应收到督办推送");

        // 去重：未处理前再次扫描不重复生成
        analysisService.generateReminders();
        assertEquals(1, reminderLogRepo.findByStatusOrderByIdDesc(ReminderLog.STATUS_OPEN).stream()
                .filter(x -> x.getDocId().equals(docId)).count());

        // 处理后，催办 ≥ 3 次再次触发督办
        analysisService.handleReminder(r.getId());
        assertEquals(ReminderLog.STATUS_HANDLED,
                reminderLogRepo.findById(r.getId()).orElseThrow().getStatus());
        docService.urge(docId, zhangsan);
        docService.urge(docId, zhangsan);
        docService.urge(docId, zhangsan);
        analysisService.extract();
        analysisService.generateReminders();
        ReminderLog r2 = reminderLogRepo.findByStatusOrderByIdDesc(ReminderLog.STATUS_OPEN).stream()
                .filter(x -> x.getDocId().equals(docId)).findFirst()
                .orElseThrow(() -> new AssertionError("催办≥3次应再次生成督办单"));
        assertTrue(r2.getReason().contains("催办 3 次"), "督办原因：" + r2.getReason());
    }
}
