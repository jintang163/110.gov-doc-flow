package com.gov.gw.flow;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiException;
import com.gov.gw.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flows")
public class FlowController {
    private final FlowRepo flowRepo;
    private final FlowService flowService;
    private final AuthService authService;

    public FlowController(FlowRepo flowRepo, FlowService flowService, AuthService authService) {
        this.flowRepo = flowRepo;
        this.flowService = flowService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<FlowConfig>> list() {
        return ApiResponse.ok(flowRepo.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<FlowConfig> detail(@PathVariable Long id) {
        return ApiResponse.ok(flowRepo.findById(id).orElseThrow(() -> ApiException.notFound("流程")));
    }

    public record FlowReq(@NotBlank String name, @NotBlank String nodesJson, String remark, Boolean enabled) {}

    @PostMapping
    public ApiResponse<FlowConfig> create(@RequestBody FlowReq req) {
        authService.requireAdmin();
        return ApiResponse.ok(flowService.create(req.name(), req.nodesJson(), req.remark()));
    }

    @PutMapping("/{id}")
    public ApiResponse<FlowConfig> update(@PathVariable Long id, @RequestBody FlowReq req) {
        authService.requireAdmin();
        return ApiResponse.ok(flowService.update(id, req.name(), req.nodesJson(), req.remark(), req.enabled()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authService.requireAdmin();
        flowRepo.deleteById(id);
        return ApiResponse.ok();
    }
}
