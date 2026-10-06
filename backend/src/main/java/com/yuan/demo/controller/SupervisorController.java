package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.entity.Supervisor;
import com.yuan.demo.security.LoginAuditService;
import com.yuan.demo.security.PasswordService;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.security.SessionAuth;
import com.yuan.demo.service.ISupervisorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 公众监督员注册、登录及管理员维护接口。 */
@RestController
@RequestMapping("/supervisor")
@RequiredArgsConstructor
public class SupervisorController {
    private final ISupervisorService supervisorService;
    private final PasswordService passwordService;
    private final LoginAuditService auditService;

    @PostMapping("/register")
    @Transactional
    public ResultVO<String> register(@RequestBody Map<String, Object> params) {
        Supervisor supervisor = fromRegistration(params);
        validateSupervisor(supervisor, true);
        if (supervisorService.getById(supervisor.getTelId()) != null) {
            return new ResultVO<>(400, "该手机号已被注册");
        }
        String rawPassword = passwordService.decryptRequestPassword(string(params.get("encryptedPassword")));
        if (rawPassword.length() < 6) throw new IllegalArgumentException("密码长度不能少于6位");
        supervisor.setPassword(passwordService.encode(rawPassword));
        if (!supervisorService.save(supervisor)) throw new IllegalStateException("注册失败");
        return new ResultVO<>(200, "注册成功", supervisor.getTelId());
    }

    @PostMapping("/login")
    public ResultVO<Map<String, Object>> login(@RequestBody Map<String, String> params,
                                               HttpServletRequest request) {
        String telId = required(params.get("telId"), "手机号码不能为空");
        String password = passwordService.decryptRequestPassword(params.get("encryptedPassword"));
        Supervisor supervisor = supervisorService.getById(telId);
        if (supervisor == null || !passwordService.matches(password, supervisor.getPassword())) {
            auditService.record("PUBLIC", telId, supervisor == null ? null : supervisor.getRealName(),
                    false, "手机号或密码错误", request);
            return new ResultVO<>(400, supervisor == null ? "该手机号未注册" : "密码错误");
        }
        Map<String, Object> loginInfo = new HashMap<>();
        loginInfo.put("telId", supervisor.getTelId());
        loginInfo.put("realName", supervisor.getRealName());
        loginInfo.put("age", supervisor.getAge());
        loginInfo.put("birthday", supervisor.getBirthday());
        loginInfo.put("sex", supervisor.getSex());
        loginInfo.put("role", "PUBLIC");
        SessionAuth.login(request, "PUBLIC", supervisor.getTelId(), loginInfo);
        auditService.record("PUBLIC", telId, supervisor.getRealName(), true, "登录成功", request);
        return new ResultVO<>(200, "登录成功", loginInfo);
    }

    @GetMapping("/list")
    @RequireRole("ADMIN")
    public ResultVO<List<Supervisor>> list() {
        List<Supervisor> supervisors = supervisorService.list();
        supervisors.forEach(item -> item.setPassword(null));
        return new ResultVO<>(200, "查询成功", supervisors);
    }

    @GetMapping("/find/{telId}")
    @RequireRole("ADMIN")
    public ResultVO<Supervisor> find(@PathVariable String telId) {
        Supervisor supervisor = supervisorService.getById(telId);
        if (supervisor == null) return new ResultVO<>(404, "未找到该公众监督员");
        supervisor.setPassword(null);
        return new ResultVO<>(200, "查询成功", supervisor);
    }

    @PostMapping("/update")
    @RequireRole("ADMIN")
    public ResultVO<Boolean> update(@RequestBody Supervisor supervisor) {
        validateSupervisor(supervisor, false);
        Supervisor stored = supervisorService.getById(supervisor.getTelId());
        if (stored == null) return new ResultVO<>(404, "未找到该公众监督员", false);
        supervisor.setPassword(stored.getPassword());
        return new ResultVO<>(200, "更新成功", supervisorService.updateById(supervisor));
    }

    @GetMapping("/delete/{telId}")
    @RequireRole("ADMIN")
    public ResultVO<Boolean> delete(@PathVariable String telId) {
        return new ResultVO<>(200, "删除成功", supervisorService.removeById(telId));
    }

    private Supervisor fromRegistration(Map<String, Object> params) {
        Supervisor supervisor = new Supervisor();
        supervisor.setTelId(string(params.get("telId")));
        supervisor.setRealName(string(params.get("realName")));
        supervisor.setAge(integer(params.get("age")));
        supervisor.setBirthday(string(params.get("birthday")));
        supervisor.setSex(integer(params.get("sex")));
        supervisor.setRemarks(string(params.get("remarks")));
        return supervisor;
    }

    private void validateSupervisor(Supervisor supervisor, boolean requireAge) {
        if (supervisor == null) throw new IllegalArgumentException("注册信息不能为空");
        supervisor.setTelId(required(supervisor.getTelId(), "手机号码不能为空"));
        if (!supervisor.getTelId().matches("^1[3-9]\\d{9}$")) throw new IllegalArgumentException("手机号码格式不正确");
        supervisor.setRealName(required(supervisor.getRealName(), "真实姓名不能为空"));
        if (supervisor.getRealName().length() > 50) throw new IllegalArgumentException("真实姓名不能超过50个字符");
        if (requireAge && (supervisor.getAge() == null || supervisor.getAge() < 1 || supervisor.getAge() > 120)) {
            throw new IllegalArgumentException("年龄必须在1到120岁之间");
        }
        if (supervisor.getSex() == null || (supervisor.getSex() != 0 && supervisor.getSex() != 1)) {
            throw new IllegalArgumentException("请选择正确的性别");
        }
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String string(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private Integer integer(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try { return Integer.valueOf(value.toString()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("数字字段格式不正确"); }
    }
}
