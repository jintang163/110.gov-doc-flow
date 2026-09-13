package com.gov.gw.analysis;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 效能分析看板接口（仅管理员），数据全部来自分析表 doc_analysis / reminder_log */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final AnalysisService analysisService;
    private final AuthService authService;

    public AnalysisController(AnalysisService analysisService, AuthService authService) {
        this.analysisService = analysisService;
        this.authService = authService;
    }

    /** 总览卡片 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.overview());
    }

    /** 办理时效统计：dim = org | post | template */
    @GetMapping("/efficiency")
    public ApiResponse<List<Map<String, Object>>> efficiency(@RequestParam(defaultValue = "org") String dim) {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.efficiency(dim));
    }

    /** 办结趋势（逐月） */
    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "6") int months) {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.trend(Math.min(Math.max(months, 3), 24)));
    }

    /** 各节点停留时长 */
    @GetMapping("/nodes")
    public ApiResponse<List<Map<String, Object>>> nodes() {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.nodeDurations());
    }

    /** 退回热点分析 */
    @GetMapping("/returns")
    public ApiResponse<Map<String, Object>> returns() {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.returnAnalysis());
    }

    /** 效能排行：month=yyyy-MM，type=org|user */
    @GetMapping("/ranking")
    public ApiResponse<List<Map<String, Object>>> ranking(@RequestParam String month,
                                                          @RequestParam(defaultValue = "org") String type) {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.ranking(month, type));
    }

    /** 效能排行 CSV 导出 */
    @GetMapping("/ranking/export")
    public ResponseEntity<String> rankingExport(@RequestParam String month,
                                                @RequestParam(defaultValue = "org") String type) {
        authService.requireAdmin();
        String csv = analysisService.rankingCsv(month, type);
        String filename = ("user".equals(type) ? "个人效能排行-" : "部门效能排行-") + month + ".csv";
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(csv);
    }

    /** 督办单列表 */
    @GetMapping("/reminders")
    public ApiResponse<List<ReminderLog>> reminders(@RequestParam(required = false) String status) {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.reminders(status));
    }

    /** 督办单标记已处理 */
    @PostMapping("/reminders/{id}/handle")
    public ApiResponse<ReminderLog> handleReminder(@PathVariable Long id) {
        authService.requireAdmin();
        return ApiResponse.ok(analysisService.handleReminder(id));
    }

    /** 手动触发数据抽取（立即刷新看板） */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh() {
        authService.requireAdmin();
        int docs = analysisService.extract();
        int reminders = analysisService.generateReminders();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docs", docs);
        result.put("reminders", reminders);
        return ApiResponse.ok(result);
    }
}
