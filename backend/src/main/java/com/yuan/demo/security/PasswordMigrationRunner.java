package com.yuan.demo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 首次升级时将旧版示例项目遗留的明文密码迁移为 BCrypt。 */
@Component
@Order(1)
@RequiredArgsConstructor
public class PasswordMigrationRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;

    @Override
    public void run(ApplicationArguments args) {
        migrate("supervisor", "tel_id");
        migrate("grid_member", "gm_id");
        migrate("system_account", "account_id");
    }

    private void migrate(String table, String idColumn) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + idColumn + " AS id, password FROM " + table + " WHERE password IS NOT NULL");
        for (Map<String, Object> row : rows) {
            String password = row.get("password").toString();
            if (!passwordService.isEncoded(password)) {
                jdbcTemplate.update("UPDATE " + table + " SET password = ? WHERE " + idColumn + " = ?",
                        passwordService.encode(password), row.get("id"));
            }
        }
    }
}
