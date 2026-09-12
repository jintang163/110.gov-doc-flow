package com.gov.gw;

import com.gov.gw.common.ApiException;
import com.gov.gw.doc.*;
import com.gov.gw.org.OrgRepo;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import com.gov.gw.template.TemplateRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限：密级控制、可见范围、办理权限。
 */
@SpringBootTest
class PermissionIntegrationTest {
    @Autowired DocService docService;
    @Autowired DocTaskRepo taskRepo;
    @Autowired UserRepo userRepo;
    @Autowired OrgRepo orgRepo;
    @Autowired TemplateRepo templateRepo;
    @Autowired PermissionService permissionService;

    private UserEntity user(String username) {
        return userRepo.findByUsername(username).orElseThrow();
    }

    @Test
    void clearanceAndVisibility() {
        UserEntity zhangsan = user("zhangsan");   // clearance 1
        UserEntity lisi = user("lisi");           // clearance 2
        UserEntity qianqi = user("qianqi");       // clearance 3
        Long templateId = templateRepo.findAll().get(0).getId();

        // 机密公文（密级 3）
        Document doc = docService.createDraft(new DocService.DocReq(
                "机密级测试文件", templateId, 3, "市发展改革委", null, "机密正文。", List.of()), qianqi);

        // 密级不足：张三（1）、李四（2）均不可见，即使李四后续是流程参与人
        assertFalse(permissionService.canView(doc, zhangsan));
        assertFalse(permissionService.canView(doc, lisi));
        assertThrows(ApiException.class, () -> permissionService.requireView(doc, lisi));
        // 拟稿人（密级 3）可见
        assertTrue(permissionService.canView(doc, qianqi));

        // 内部公文（密级 1）：参与者可见
        Document normal = docService.createDraft(new DocService.DocReq(
                "内部测试文件", templateId, 1, "市发展改革委", null, "正文。", List.of()), zhangsan);
        normal = docService.submit(normal.getId(), zhangsan);
        assertTrue(permissionService.canView(normal, lisi), "流程参与人可见");
        // 主送单位成员可见
        assertTrue(permissionService.canView(normal, user("wangwu")), "主送单位成员可见");

        // 与公文无关且非主送抄送单位的人员不可见：新建一名无外联用户
        UserEntity outsider = new UserEntity();
        outsider.setUsername("outsider");
        outsider.setPasswordHash("x");
        outsider.setName("局外人");
        outsider.setOrgId(orgRepo.findAll().stream()
                .filter(o -> o.getName().equals("市财政局")).findFirst().orElseThrow().getId());
        outsider.setClearance(3);
        outsider = userRepo.save(outsider);
        // 财政局不在主送中（主送是发改委），但会签节点有赵六（财政局）→ 提交后赵六是参与人；用草稿状态判断
        Document draft = docService.createDraft(new DocService.DocReq(
                "无关人员不可见", templateId, 1, "市发展改革委", null, "正文。", List.of()), zhangsan);
        assertFalse(permissionService.canView(draft, outsider), "非参与人非主送单位不可见");

        // 非办理人不能办理
        Document running = docService.submit(
                docService.createDraft(new DocService.DocReq(
                        "办理权限测试", templateId, 1, "市发展改革委", null, "正文。", List.of()), zhangsan).getId(),
                zhangsan);
        DocTask lisiTask = taskRepo.findByDocIdOrderById(running.getId()).stream()
                .filter(t -> DocTask.STATUS_PENDING.equals(t.getStatus())).findFirst().orElseThrow();
        UserEntity wangwu = user("wangwu");
        Long taskId = lisiTask.getId();
        Long docId = running.getId();
        assertThrows(ApiException.class, () ->
                docService.complete(docId, taskId, "越权办理", wangwu));
    }
}
