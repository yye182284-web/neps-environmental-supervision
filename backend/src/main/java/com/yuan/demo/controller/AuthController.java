package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.security.LoginAuditService;
import com.yuan.demo.security.PasswordService;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.security.SecretUtil;
import com.yuan.demo.security.SessionAuth;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 网格员、系统管理员和决策者登录接口。 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "角色登录", description = "RSA 加密传输、BCrypt 密码校验和服务端会话")
public class AuthController {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final SecretUtil secretUtil;
    private final LoginAuditService auditService;

    @GetMapping("/public-key")
    public ResultVO<String> publicKey() {
        return new ResultVO<>(200, "获取成功", secretUtil.publicKeyPem());
    }

    @GetMapping("/me")
    @RequireRole
    public ResultVO<Map<String, Object>> me(HttpServletRequest request) {
        return new ResultVO<>(200, "会话有效", SessionAuth.user(request));
    }

    @PostMapping("/logout")
    @RequireRole
    public ResultVO<Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return new ResultVO<>(200, "退出成功", true);
    }

    @PostMapping("/grid/login")
    public ResultVO<Map<String, Object>> gridLogin(@RequestBody Map<String, String> params,
                                                   HttpServletRequest request) {
        String account = required(params.get("account"), "网格员编号或登录编码不能为空");
        String password = passwordService.decryptRequestPassword(params.get("encryptedPassword"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT gm_id AS `gmId`, gm_name AS `realName`, gm_code AS `gmCode`, password,
                       province_id AS `provinceId`, city_id AS `cityId`, tel, state
                FROM grid_member
                WHERE (gm_id = ? OR gm_code = ? OR tel = ?)
                LIMIT 1
                """, account, account, account);
        if (rows.isEmpty() || !passwordService.matches(password, stringValue(rows.get(0).get("password")))) {
            auditService.record("GRID_MEMBER", account, null, false, "账号或密码错误", request);
            return new ResultVO<>(400, "网格员账号或密码错误");
        }
        Map<String, Object> row = rows.get(0);
        if (numberValue(row.get("state")) == 3) {
            auditService.record("GRID_MEMBER", account, stringValue(row.get("realName")), false, "账号不可用", request);
            return new ResultVO<>(403, "该网格员账号当前不可用");
        }
        Map<String, Object> user = new LinkedHashMap<>(row);
        user.remove("password");
        user.put("role", "GRID_MEMBER");
        SessionAuth.login(request, "GRID_MEMBER", stringValue(user.get("gmId")), user);
        auditService.record("GRID_MEMBER", account, stringValue(user.get("realName")), true, "登录成功", request);
        return new ResultVO<>(200, "登录成功", user);
    }

    @PostMapping("/staff/login")
    public ResultVO<Map<String, Object>> staffLogin(@RequestBody Map<String, String> params,
                                                    HttpServletRequest request) {
        String username = required(params.get("username"), "登录账号不能为空");
        String password = passwordService.decryptRequestPassword(params.get("encryptedPassword"));
        String role = required(params.get("role"), "请选择登录角色").toUpperCase();
        if (!"ADMIN".equals(role) && !"DECISION".equals(role)) {
            throw new IllegalArgumentException("登录角色不正确");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT account_id AS `accountId`, username, real_name AS `realName`, role, state, password
                FROM system_account
                WHERE username = ? AND role = ?
                LIMIT 1
                """, username, role);
        if (rows.isEmpty() || !passwordService.matches(password, stringValue(rows.get(0).get("password")))) {
            auditService.record(role, username, null, false, "账号、密码或角色错误", request);
            return new ResultVO<>(400, "账号、密码或角色不正确");
        }
        Map<String, Object> row = rows.get(0);
        if (numberValue(row.get("state")) != 0) {
            auditService.record(role, username, stringValue(row.get("realName")), false, "账号已停用", request);
            return new ResultVO<>(403, "该账号已停用");
        }
        Map<String, Object> user = new LinkedHashMap<>(row);
        user.remove("password");
        user.remove("state");
        SessionAuth.login(request, role, stringValue(user.get("accountId")), user);
        auditService.record(role, username, stringValue(user.get("realName")), true, "登录成功", request);
        return new ResultVO<>(200, "登录成功", user);
    }

    @GetMapping("/login-logs")
    @RequireRole("ADMIN")
    public ResultVO<List<Map<String, Object>>> loginLogs(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return new ResultVO<>(200, "查询成功", jdbcTemplate.queryForList("""
                SELECT audit_id AS `auditId`, account_type AS `accountType`, account_name AS `accountName`,
                       real_name AS `realName`, success, client_ip AS `clientIp`,
                       login_time AS `loginTime`, message
                FROM login_audit ORDER BY login_time DESC LIMIT ?
                """, safeLimit));
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }
}
