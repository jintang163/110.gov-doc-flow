package com.gov.gw.org;

import com.gov.gw.auth.AuthService;
import com.gov.gw.common.ApiException;
import com.gov.gw.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepo userRepo;
    private final AuthService authService;

    public UserController(UserRepo userRepo, AuthService authService) {
        this.userRepo = userRepo;
        this.authService = authService;
    }

    /** 人员选择器用：全部启用用户；all=true（仅管理员）返回含停用在内的全部用户 */
    @GetMapping
    public ApiResponse<List<UserEntity>> list(@RequestParam(required = false) Long orgId,
                                              @RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            authService.requireAdmin();
            return ApiResponse.ok(userRepo.findAll());
        }
        List<UserEntity> users = orgId == null ? userRepo.findByEnabledTrue() : userRepo.findByOrgId(orgId);
        return ApiResponse.ok(users);
    }

    public record UserReq(@NotBlank String username, String password, @NotBlank String name,
                          Long orgId, String posts, Integer clearance, Boolean admin, Boolean enabled) {}

    @PostMapping
    public ApiResponse<UserEntity> create(@RequestBody UserReq req) {
        authService.requireAdmin();
        if (userRepo.findByUsername(req.username()).isPresent()) {
            throw new ApiException("用户名已存在");
        }
        UserEntity u = new UserEntity();
        apply(u, req);
        u.setPasswordHash(authService.hashPassword(
                req.password() == null || req.password().isBlank() ? "123456" : req.password()));
        return ApiResponse.ok(userRepo.save(u));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserEntity> update(@PathVariable Long id, @RequestBody UserReq req) {
        authService.requireAdmin();
        UserEntity u = userRepo.findById(id).orElseThrow(() -> ApiException.notFound("用户"));
        apply(u, req);
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(authService.hashPassword(req.password()));
        }
        return ApiResponse.ok(userRepo.save(u));
    }

    public record PwdReq(@NotBlank String oldPassword, @NotBlank String newPassword) {}

    @PostMapping("/me/password")
    public ApiResponse<Void> changePassword(@RequestBody PwdReq req) {
        UserEntity me = authService.current();
        if (!authService.checkPassword(me, req.oldPassword())) {
            throw new ApiException("原密码不正确");
        }
        me.setPasswordHash(authService.hashPassword(req.newPassword()));
        userRepo.save(me);
        return ApiResponse.ok();
    }

    private void apply(UserEntity u, UserReq req) {
        u.setUsername(req.username());
        u.setName(req.name());
        u.setOrgId(req.orgId() == null ? 0L : req.orgId());
        u.setPosts(req.posts() == null ? "" : req.posts());
        u.setClearance(req.clearance() == null ? 1 : req.clearance());
        u.setAdmin(Boolean.TRUE.equals(req.admin()));
        u.setEnabled(req.enabled() == null || req.enabled());
    }
}
