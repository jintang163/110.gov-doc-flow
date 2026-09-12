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
 * 会签模式：或签（一人办理即过，其余自动取消）、串签（依次办理）。
 */
@SpringBootTest
class CountersignModeIntegrationTest {
    @Autowired DocService docService;
    @Autowired DocTaskRepo taskRepo;
    @Autowired UserRepo userRepo;
    @Autowired TemplateRepo templateRepo;

    private UserEntity user(String username) {
        return userRepo.findByUsername(username).orElseThrow();
    }

    private List<DocTask> pendingTasks(Long docId) {
        return taskRepo.findByDocIdOrderById(docId).stream()
                .filter(t -> DocTask.STATUS_PENDING.equals(t.getStatus())).toList();
    }

    @Test
    void anyAndSequenceModes() {
        UserEntity zhangsan = user("zhangsan");
        UserEntity lisi = user("lisi");
        UserEntity wangwu = user("wangwu");
        UserEntity zhaoliu = user("zhaoliu");
        UserEntity qianqi = user("qianqi");
        Long templateId = templateRepo.findAll().stream()
                .filter(t -> t.getName().equals("市政府办公室文件")).findFirst().orElseThrow().getId();

        Document doc = docService.createDraft(new DocService.DocReq(
                "或签串签模式测试", templateId, 1, "市发展改革委", null, "正文。", List.of()), zhangsan);
        doc = docService.submit(doc.getId(), zhangsan);

        // 或签节点：李四、王五均有待办；李四办理后王五任务自动取消，流程前进
        assertEquals("audit2", doc.getCurrentNodeKey());
        List<DocTask> auditTasks = pendingTasks(doc.getId());
        assertEquals(2, auditTasks.size());
        DocTask lisiTask = auditTasks.stream()
                .filter(t -> t.getAssigneeId().equals(lisi.getId())).findFirst().orElseThrow();
        doc = docService.complete(doc.getId(), lisiTask.getId(), "同意", lisi);
        assertEquals("seq", doc.getCurrentNodeKey());
        assertTrue(taskRepo.findByDocIdOrderById(doc.getId()).stream()
                .filter(t -> t.getNodeKey().equals("audit2"))
                .filter(t -> t.getAssigneeId().equals(wangwu.getId()))
                .allMatch(t -> DocTask.STATUS_CANCELED.equals(t.getStatus())),
                "或签未办理人的任务应被取消");

        // 串签节点：先赵六后王五，同一时刻只有一人有待办
        List<DocTask> seqPending = pendingTasks(doc.getId());
        assertEquals(1, seqPending.size());
        assertEquals(zhaoliu.getId(), seqPending.get(0).getAssigneeId());
        doc = docService.complete(doc.getId(), seqPending.get(0).getId(), "同意", zhaoliu);
        seqPending = pendingTasks(doc.getId());
        assertEquals(1, seqPending.size());
        assertEquals(wangwu.getId(), seqPending.get(0).getAssigneeId());
        doc = docService.complete(doc.getId(), seqPending.get(0).getId(), "同意", wangwu);

        // 签发 → 归档
        assertEquals("issue2", doc.getCurrentNodeKey());
        DocTask issueTask = pendingTasks(doc.getId()).get(0);
        assertEquals(qianqi.getId(), issueTask.getAssigneeId());
        doc = docService.complete(doc.getId(), issueTask.getId(), "同意印发", qianqi);
        assertEquals(Document.STATUS_ARCHIVED, doc.getStatus());
        assertTrue(doc.getDocNo().startsWith("XX政办发〔"));
    }
}
