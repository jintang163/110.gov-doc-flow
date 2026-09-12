package com.gov.gw.auth;

import com.gov.gw.common.ApiResponse;
import com.gov.gw.org.OrgEntity;
import com.gov.gw.org.OrgRepo;
import com.gov.gw.org.UserEntity;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final OrgRepo orgRepo;

    public AuthController(AuthService authService, OrgRepo orgRepo) {
        this.authService = authService;
        this.orgRepo = orgRepo;
    }

    public record LoginReq(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginReq req) {
        String token = authService.login(req.username(), req.password());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("user", profile(authService.authenticate(token)));
        return ApiResponse.ok(data);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7));
        }
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(profile(authService.current()));
    }

    private Map<String, Object> profile(UserEntity u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("name", u.getName());
        m.put("orgId", u.getOrgId());
        m.put("orgName", orgRepo.findById(u.getOrgId()).map(OrgEntity::getName).orElse(""));
        m.put("posts", u.getPosts());
        m.put("clearance", u.getClearance());
        m.put("admin", u.getAdmin());
        return m;
    }
}
