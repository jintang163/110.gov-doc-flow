package com.gov.gw.auth;

import com.gov.gw.common.ApiException;
import com.gov.gw.org.UserEntity;
import com.gov.gw.org.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {
    private static final ThreadLocal<UserEntity> CURRENT = new ThreadLocal<>();

    private final UserRepo userRepo;
    private final TokenStore tokenStore;
    private final long ttlSeconds;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepo userRepo, TokenStore tokenStore,
                       @Value("${app.token-ttl-hours:12}") long ttlHours) {
        this.userRepo = userRepo;
        this.tokenStore = tokenStore;
        this.ttlSeconds = ttlHours * 3600;
    }

    public String hashPassword(String raw) {
        return encoder.encode(raw);
    }

    public boolean checkPassword(UserEntity user, String raw) {
        return encoder.matches(raw, user.getPasswordHash());
    }

    /** 登录，返回 token */
    public String login(String username, String password) {
        UserEntity user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ApiException(401, "用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ApiException(403, "账号已停用");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(401, "用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, user.getId(), ttlSeconds);
        return token;
    }

    public void logout(String token) {
        if (token != null) tokenStore.remove(token);
    }

    /** 由拦截器调用：按 token 解析用户并绑定到当前线程 */
    public UserEntity authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        return tokenStore.get(token)
                .flatMap(userRepo::findById)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElse(null);
    }

    public void bind(UserEntity user) {
        CURRENT.set(user);
    }

    public void clear() {
        CURRENT.remove();
    }

    /** 当前登录用户，未登录抛 401 */
    public UserEntity current() {
        UserEntity u = CURRENT.get();
        if (u == null) throw new ApiException(401, "未登录或登录已过期");
        return u;
    }

    public Long currentId() {
        return current().getId();
    }

    public void requireAdmin() {
        if (!Boolean.TRUE.equals(current().getAdmin())) {
            throw ApiException.forbidden("需要管理员权限");
        }
    }
}
