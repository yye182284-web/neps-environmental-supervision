package com.yuan.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginAuditService {
    private final JdbcTemplate jdbcTemplate;

    public void record(String role, String account, String realName, boolean success,
                       String message, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        jdbcTemplate.update("""
                INSERT INTO login_audit(account_type, account_name, real_name, success, client_ip, message)
                VALUES (?, ?, ?, ?, ?, ?)
                """, role, account, realName, success, ip, message);
    }
}
