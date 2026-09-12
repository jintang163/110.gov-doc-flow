package com.gov.gw.template;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiException;
import com.gov.gw.common.ApiResponse;
import com.gov.gw.flow.FlowRepo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final TemplateRepo templateRepo;
    private final FlowRepo flowRepo;
    private final AuthService authService;

    public TemplateController(TemplateRepo templateRepo, FlowRepo flowRepo, AuthService authService) {
        this.templateRepo = templateRepo;
        this.flowRepo = flowRepo;
        this.authService = authService;
    }

    /** 拟稿可选模板 */
    @GetMapping
    public ApiResponse<List<DocTemplate>> list(@RequestParam(defaultValue = "false") boolean all) {
        authService.current();
        return ApiResponse.ok(all ? templateRepo.findAll() : templateRepo.findByEnabledTrue());
    }

    public record TemplateReq(@NotBlank String name, @NotBlank String redTitle, @NotBlank String noPrefix,
                              @NotBlank String issuer, @NotNull Long flowId, Boolean enabled, String remark) {}

    @PostMapping
    public ApiResponse<DocTemplate> create(@RequestBody TemplateReq req) {
        authService.requireAdmin();
        DocTemplate t = new DocTemplate();
        apply(t, req);
        return ApiResponse.ok(templateRepo.save(t));
    }

    @PutMapping("/{id}")
    public ApiResponse<DocTemplate> update(@PathVariable Long id, @RequestBody TemplateReq req) {
        authService.requireAdmin();
        DocTemplate t = templateRepo.findById(id).orElseThrow(() -> ApiException.notFound("模板"));
        apply(t, req);
        return ApiResponse.ok(templateRepo.save(t));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authService.requireAdmin();
        templateRepo.deleteById(id);
        return ApiResponse.ok();
    }

    private void apply(DocTemplate t, TemplateReq req) {
        flowRepo.findById(req.flowId()).orElseThrow(() -> new ApiException("绑定的流程不存在"));
        t.setName(req.name());
        t.setRedTitle(req.redTitle());
        t.setNoPrefix(req.noPrefix());
        t.setIssuer(req.issuer());
        t.setFlowId(req.flowId());
        t.setEnabled(req.enabled() == null || req.enabled());
        t.setRemark(req.remark());
    }
}
