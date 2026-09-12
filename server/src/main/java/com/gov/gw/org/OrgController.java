package com.gov.gw.org;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiException;
import com.gov.gw.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orgs")
public class OrgController {
    private final OrgRepo orgRepo;
    private final UserRepo userRepo;
    private final AuthService authService;

    public OrgController(OrgRepo orgRepo, UserRepo userRepo, AuthService authService) {
        this.orgRepo = orgRepo;
        this.userRepo = userRepo;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<OrgEntity>> list() {
        return ApiResponse.ok(orgRepo.findAll());
    }

    public record OrgReq(@NotBlank String name, Long parentId, Long leaderId, Integer sort) {}

    @PostMapping
    public ApiResponse<OrgEntity> create(@RequestBody OrgReq req) {
        authService.requireAdmin();
        OrgEntity o = new OrgEntity();
        apply(o, req);
        return ApiResponse.ok(orgRepo.save(o));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrgEntity> update(@PathVariable Long id, @RequestBody OrgReq req) {
        authService.requireAdmin();
        OrgEntity o = orgRepo.findById(id).orElseThrow(() -> ApiException.notFound("部门"));
        apply(o, req);
        return ApiResponse.ok(orgRepo.save(o));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authService.requireAdmin();
        if (!userRepo.findByOrgId(id).isEmpty()) {
            throw new ApiException("部门下仍有用户，不能删除");
        }
        orgRepo.deleteById(id);
        return ApiResponse.ok();
    }

    private void apply(OrgEntity o, OrgReq req) {
        o.setName(req.name());
        o.setParentId(req.parentId() == null ? 0L : req.parentId());
        o.setLeaderId(req.leaderId());
        o.setSort(req.sort() == null ? 0 : req.sort());
    }
}
