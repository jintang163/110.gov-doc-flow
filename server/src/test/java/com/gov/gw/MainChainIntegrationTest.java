package com.gov.gw;

import com.gov.gw.doc.*;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import com.gov.gw.seal.SealRecord;
import com.gov.gw.seal.SealRepo;
import com.gov.gw.template.TemplateRepo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 主链路：拟稿 → 审核 → 会签(全部) → 签发(取号/套红/盖章) → 归档 → 验章。
 */
@SpringBootTest
class MainChainIntegrationTest {
    @Autowired DocService docService;
    @Autowired DocumentRepo documentRepo;
    @Autowired DocTaskRepo taskRepo;
    @Autowired DocTraceRepo traceRepo;
    @Autowired UserRepo userRepo;
    @Autowired TemplateRepo templateRepo;
    @Autowired SealRepo sealRepo;
    @Autowired com.gov.gw.storage.StorageService storage;

    private UserEntity user(String username) {
        return userRepo.findByUsername(username).orElseThrow();
    }

    private DocTask pendingOf(Long docId, Long assigneeId) {
        return taskRepo.findByDocIdOrderById(docId).stream()
                .filter(t -> DocTask.STATUS_PENDING.equals(t.getStatus()))
                .filter(t -> t.getAssigneeId().equals(assigneeId))
                .findFirst().orElseThrow(() -> new AssertionError("应有待办任务 assignee=" + assigneeId));
    }

    @Test
    void fullChain() throws Exception {
        UserEntity zhangsan = user("zhangsan");
        UserEntity lisi = user("lisi");
        UserEntity wangwu = user("wangwu");
        UserEntity zhaoliu = user("zhaoliu");
        UserEntity qianqi = user("qianqi");
        Long templateId = templateRepo.findAll().stream()
                .filter(t -> t.getName().equals("市政府文件")).findFirst().orElseThrow().getId();

        // 拟稿
        Document doc = docService.createDraft(new DocService.DocReq(
                "关于印发城市更新三年行动方案的通知", templateId, 1,
                "市发展改革委，市财政局", "市政府办公室",
                "为推进城市更新，现就有关事项通知如下。\n一、提高站位，加强组织领导。\n二、突出重点，狠抓任务落实。",
                List.of(Map.of("name", "行动方案.pdf", "key", "misc/a.pdf", "size", 1024))),
                zhangsan);
        assertEquals(Document.STATUS_DRAFT, doc.getStatus());

        // 提交
        doc = docService.submit(doc.getId(), zhangsan);
        assertEquals(Document.STATUS_RUNNING, doc.getStatus());
        assertEquals("audit", doc.getCurrentNodeKey());
        assertNotNull(doc.getProcessInstanceId());

        // 张三不能办理李四的任务
        DocTask auditTask = pendingOf(doc.getId(), lisi.getId());
        Long auditDocId = doc.getId();
        Long auditTaskId = auditTask.getId();
        assertThrows(Exception.class, () ->
                docService.complete(auditDocId, auditTaskId, "越权", zhangsan));

        // 审核通过
        doc = docService.complete(doc.getId(), auditTask.getId(), "同意，请会签", lisi);
        assertEquals("countersign", doc.getCurrentNodeKey());

        // 会签：两人都有待办，一人办理后流程不前进
        DocTask t1 = pendingOf(doc.getId(), wangwu.getId());
        DocTask t2 = pendingOf(doc.getId(), zhaoliu.getId());
        doc = docService.complete(doc.getId(), t1.getId(), "同意", wangwu);
        assertEquals("countersign", doc.getCurrentNodeKey());
        doc = docService.complete(doc.getId(), t2.getId(), "同意", zhaoliu);

        // 到达签发：已取号 + 已套红
        assertEquals("issue", doc.getCurrentNodeKey());
        assertNotNull(doc.getDocNo());
        assertTrue(doc.getDocNo().startsWith("XX政发〔"), "文号格式：" + doc.getDocNo());
        assertNotNull(doc.getPdfKey());

        // 套红 PDF 内容校验
        byte[] pdfBytes = storage.getBytes(doc.getPdfKey());
        assertTrue(pdfBytes.length > 1000);
        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            assertTrue(pdf.getNumberOfPages() >= 1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("XX市人民政府文件"), "红头大字缺失");
            assertTrue(text.contains(doc.getDocNo()), "文号缺失");
            assertTrue(text.contains("关于印发城市更新三年行动方案的通知"), "标题缺失");
            assertTrue(text.contains("市发展改革委"), "主送缺失");
            assertTrue(text.contains("抄送"), "抄送栏缺失");
            assertTrue(text.contains("XX市人民政府"), "落款缺失");
        }

        // 盖章：定位章 + 骑缝章
        var seal = sealRepo.findAll().stream()
                .filter(s -> s.getName().contains("公章")).findFirst().orElseThrow();
        DocTask issueTask = pendingOf(doc.getId(), qianqi.getId());
        SealRecord r1 = docService.seal(doc.getId(), seal.getId(), "LOCATE", null, null, null, null, qianqi);
        assertEquals("LOCATE", r1.getType());
        assertNotNull(r1.getPdfHash());
        SealRecord r2 = docService.seal(doc.getId(), seal.getId(), "STITCH", null, null, null, null, qianqi);
        assertEquals("STITCH", r2.getType());
        doc = documentRepo.findById(doc.getId()).orElseThrow();
        assertEquals(r2.getPdfKey(), doc.getPdfKey(), "公文 PDF 应指向最新盖章版本");

        // 签发完成 → 自动归档
        doc = docService.complete(doc.getId(), issueTask.getId(), "同意印发", qianqi);
        assertEquals(Document.STATUS_ARCHIVED, doc.getStatus());
        assertNotNull(doc.getArchivedAt());
        assertNotNull(doc.getIssuedAt());

        // 验章
        Map<String, Object> verify = docService.sealVerify(doc.getId(), qianqi);
        assertEquals(Boolean.TRUE, verify.get("currentValid"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) verify.get("records");
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(r -> Boolean.TRUE.equals(r.get("valid"))));

        // 留痕完整
        List<DocTrace> traces = traceRepo.findByDocIdOrderById(doc.getId());
        assertTrue(traces.stream().anyMatch(t -> t.getAction().equals("SUBMIT")));
        assertTrue(traces.stream().anyMatch(t -> t.getAction().equals("APPROVE")));
        assertTrue(traces.stream().anyMatch(t -> t.getAction().equals("SEAL")));
        assertTrue(traces.stream().anyMatch(t -> t.getAction().equals("ARCHIVE")));
    }
}
