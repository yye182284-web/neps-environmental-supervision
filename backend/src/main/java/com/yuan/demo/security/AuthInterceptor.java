package com.yuan.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuan.demo.common.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod method)) return true;
        RequireRole rule = method.getMethodAnnotation(RequireRole.class);
        if (rule == null) rule = method.getBeanType().getAnnotation(RequireRole.class);
        if (rule == null) return true;

        String role = SessionAuth.role(request);
        if (role == null) {
            writeError(response, 401, "登录已失效，请重新登录");
            return false;
        }
        if (rule.value().length > 0 && Arrays.stream(rule.value()).noneMatch(role::equals)) {
            writeError(response, 403, "当前账号没有执行此操作的权限");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ResultVO<>(code, message)));
    }
}
