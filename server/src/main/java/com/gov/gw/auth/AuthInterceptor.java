package com.gov.gw.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.gw.common.ApiResponse;
import com.gov.gw.org.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/** 鉴权拦截器：Authorization: Bearer <token>，文件下载等场景兼容 ?token= 参数 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        UserEntity user = authService.authenticate(token);
        if (user == null) {
            response.setStatus(200); // 统一 200 + 业务码，前端拦截 code=401 跳登录
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, "未登录或登录已过期")));
            return false;
        }
        authService.bind(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        authService.clear();
    }

    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return request.getParameter("token");
    }
}
