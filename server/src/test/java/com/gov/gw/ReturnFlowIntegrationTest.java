package com.gov.gw;

import com.gov.gw.doc.*;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import com.gov.gw.template.TemplateRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 退回/补正：退回拟稿（文号作废）→ 补正重报；节点间退回上一步。
 */
@SpringBootTest
class ReturnFlowIntegrationTest {
    @Autowired DocService docService;
    @Autowired DocumentRepo documentRepo;
    @Autowired DocTaskRepo taskRepo;
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

    private Document newDoc(Long templateId, UserEntity creator) {
        return docService.createDraft(new DocService.DocReq(
                "关于退回补正的测试文件", templateId, 1,
                "市发展改革委", "市政府办公室", "正文内容。", List.of()), creator);
    }

    @Test
    void returnToDraftAndResubmit() {
        UserEntity zhangsan = user("zhangsan");
        UserEntity lisi = user("lisi");
        UserEntity wangwu = user("wangwu");
        UserEntity zhaoliu = user("zhaoliu");
        UserEntity qianqi = user("qianqi");
        Long templateId = templateRepo.findAll().stream()
                .filter(t -> t.getName().equals("市政府文件")).findFirst().orElseThrow().getId();

        // 到签发节点取号后，签发人退回拟稿 → 文号作废
        Document doc = docService.submit(newDoc(templateId, zhangsan).getId(), zhangsan);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), lisi.getId()).getId(), "同意", lisi);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), wangwu.getId()).getId(), "同意", wangwu);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), zhaoliu.getId()).getId(), "同意", zhaoliu);
        assertEquals("issue", doc.getCurrentNodeKey());
        String voidedNo = doc.getDocNo();
        assertNotNull(voidedNo);

        doc = docService.returnDraft(doc.getId(), pendingOf(doc.getId(), qianqi.getId()).getId(),
                "数据有误，请补正", qianqi);
        assertEquals(Document.STATUS_RETURNED, doc.getStatus());
        assertNull(doc.getDocNo(), "退回拟稿后文号作废");
        assertNull(doc.getProcessInstanceId());

        // 补正后重新提交，走新流程实例
        docService.updateDraft(doc.getId(), new DocService.DocReq(
                "关于退回补正的测试文件（补正）", templateId, 1,
                "市发展改革委", "市政府办公室", "正文内容（已补正）。", List.of()), zhangsan);
        doc = docService.submit(doc.getId(), zhangsan);
        assertEquals(Document.STATUS_RUNNING, doc.getStatus());
        assertEquals(2, doc.getAttempt());
        assertEquals("audit", doc.getCurrentNodeKey());

        // 再次走到签发：取得新文号（旧号不回收）
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), lisi.getId()).getId(), "同意", lisi);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), wangwu.getId()).getId(), "同意", wangwu);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), zhaoliu.getId()).getId(), "同意", zhaoliu);
        assertEquals("issue", doc.getCurrentNodeKey());
        assertNotNull(doc.getDocNo());
        assertNotEquals(voidedNo, doc.getDocNo(), "作废文号不应回收复用");
    }

    @Test
    void returnToPreviousNode() {
        UserEntity zhangsan = user("zhangsan");
        UserEntity lisi = user("lisi");
        UserEntity wangwu = user("wangwu");
        UserEntity zhaoliu = user("zhaoliu");
        Long templateId = templateRepo.findAll().stream()
                .filter(t -> t.getName().equals("市政府文件")).findFirst().orElseThrow().getId();

        Document doc = docService.submit(newDoc(templateId, zhangsan).getId(), zhangsan);
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), lisi.getId()).getId(), "同意", lisi);
        assertEquals("countersign", doc.getCurrentNodeKey());

        // 会签节点退回上一步（审核）
        doc = docService.returnPrev(doc.getId(), pendingOf(doc.getId(), wangwu.getId()).getId(),
                "请审核人复核数据", wangwu);
        assertEquals("audit", doc.getCurrentNodeKey());
        // 王五/赵六的会签任务被标记退回
        assertTrue(taskRepo.findByDocIdOrderById(doc.getId()).stream()
                .filter(t -> t.getNodeKey().equals("countersign"))
                .allMatch(t -> DocTask.STATUS_RETURNED.equals(t.getStatus())
                        || DocTask.STATUS_CANCELED.equals(t.getStatus())));
        // 李四重新办理 → 再次会签 → 两人都有新待办
        doc = docService.complete(doc.getId(), pendingOf(doc.getId(), lisi.getId()).getId(), "复核无误", lisi);
        assertEquals("countersign", doc.getCurrentNodeKey());
        pendingOf(doc.getId(), wangwu.getId());
        pendingOf(doc.getId(), zhaoliu.getId());

        // 首节点退回上一步 = 退回拟稿
        doc = docService.returnPrev(doc.getId(), pendingOf(doc.getId(), wangwu.getId()).getId(), "再退", wangwu);
        assertEquals("audit", doc.getCurrentNodeKey());
        doc = docService.returnPrev(doc.getId(), pendingOf(doc.getId(), lisi.getId()).getId(), "退回拟稿人", lisi);
        assertEquals(Document.STATUS_RETURNED, doc.getStatus());
    }
}
