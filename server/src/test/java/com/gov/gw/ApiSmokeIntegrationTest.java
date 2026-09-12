package com.gov.gw;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REST 层冒烟：登录 → 模板 → 拟稿 → 提交 → 待办 → 办理，验证鉴权与 API 契约。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSmokeIntegrationTest {
    @Autowired TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String username, String password) {
        ResponseEntity<Map> res = rest.postForEntity("/api/auth/login",
                Map.of("username", username, "password", password), Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(0, ((Number) body.get("code")).intValue(), "登录失败：" + body);
        return (Map<String, Object>) body.get("data");
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void restSmoke() {
        // 健康检查无需登录
        ResponseEntity<Map> health = rest.getForEntity("/api/health", Map.class);
        assertEquals(0, ((Number) health.getBody().get("code")).intValue());

        // 未登录访问受保护接口 → 业务码 401
        ResponseEntity<Map> unauth = rest.getForEntity("/api/docs/tasks/todo", Map.class);
        assertEquals(401, ((Number) unauth.getBody().get("code")).intValue());

        // 张三登录
        String zhangsanToken = (String) login("zhangsan", "123456").get("token");

        // 模板列表
        ResponseEntity<Map> templates = rest.exchange("/api/templates", HttpMethod.GET,
                new HttpEntity<>(auth(zhangsanToken)), Map.class);
        List<Map<String, Object>> tplList = (List<Map<String, Object>>) templates.getBody().get("data");
        assertFalse(tplList.isEmpty());
        Long templateId = ((Number) tplList.get(0).get("id")).longValue();

        // 拟稿
        ResponseEntity<Map> created = rest.exchange("/api/docs", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "title", "REST 冒烟测试文件",
                        "templateId", templateId,
                        "secretLevel", 1,
                        "mainSend", "市发展改革委",
                        "content", "正文内容。"), auth(zhangsanToken)), Map.class);
        assertEquals(0, ((Number) created.getBody().get("code")).intValue(), "拟稿失败：" + created.getBody());
        Long docId = ((Number) ((Map<String, Object>) created.getBody().get("data")).get("id")).longValue();

        // 提交
        ResponseEntity<Map> submitted = rest.exchange("/api/docs/" + docId + "/submit", HttpMethod.POST,
                new HttpEntity<>(auth(zhangsanToken)), Map.class);
        assertEquals(0, ((Number) submitted.getBody().get("code")).intValue(), "提交失败：" + submitted.getBody());

        // 李四待办中出现该公文
        String lisiToken = (String) login("lisi", "123456").get("token");
        ResponseEntity<Map> todo = rest.exchange("/api/docs/tasks/todo", HttpMethod.GET,
                new HttpEntity<>(auth(lisiToken)), Map.class);
        List<Map<String, Object>> todos = (List<Map<String, Object>>) todo.getBody().get("data");
        Map<String, Object> myTask = todos.stream()
                .filter(t -> ((Number) t.get("docId")).longValue() == docId)
                .findFirst().orElseThrow(() -> new AssertionError("李四待办中应有该公文"));

        // 李四办理
        ResponseEntity<Map> completed = rest.exchange("/api/docs/" + docId + "/complete", HttpMethod.POST,
                new HttpEntity<>(Map.of("taskId", myTask.get("id"), "comment", "同意"), auth(lisiToken)), Map.class);
        assertEquals(0, ((Number) completed.getBody().get("code")).intValue(), "办理失败：" + completed.getBody());

        // 详情可查（含留痕）
        ResponseEntity<Map> detail = rest.exchange("/api/docs/" + docId, HttpMethod.GET,
                new HttpEntity<>(auth(lisiToken)), Map.class);
        Map<String, Object> detailData = (Map<String, Object>) detail.getBody().get("data");
        assertNotNull(detailData.get("traces"));
        assertEquals("countersign", ((Map<String, Object>) detailData.get("doc")).get("currentNodeKey"));
    }
}
