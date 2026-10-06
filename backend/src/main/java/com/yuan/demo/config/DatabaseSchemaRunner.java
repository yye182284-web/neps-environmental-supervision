package com.yuan.demo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 对旧版数据库做最小的兼容升级。
 * MySQL 不同版本对 ADD COLUMN IF NOT EXISTS 的支持不一致，因此先查询元数据再修改。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DatabaseSchemaRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer ageColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'supervisor'
                  AND column_name = 'age'
                """, Integer.class);
        if (ageColumnCount == null || ageColumnCount == 0) {
            jdbcTemplate.execute("ALTER TABLE supervisor "
                    + "ADD COLUMN age TINYINT UNSIGNED NULL COMMENT '年龄（注册时填写）' AFTER real_name");
        }
        addColumnIfMissing("supervisor", "province_id", "INT NULL COMMENT '常驻省份编号'");
        addColumnIfMissing("supervisor", "city_id", "INT NULL COMMENT '常驻城市编号'");
        addColumnIfMissing("aqi_feedback", "accepted_at", "DATETIME NULL COMMENT '网格员接单时间'");
        addColumnIfMissing("aqi_feedback", "accept_deadline", "DATETIME NULL COMMENT '接单截止时间'");
        // 兼容增加接单时限功能之前已指派的历史任务，避免其因截止时间为空而无法接单。
        jdbcTemplate.update("""
                UPDATE aqi_feedback
                SET accept_deadline = DATE_ADD(NOW(), INTERVAL 30 MINUTE)
                WHERE state = 1 AND gm_id IS NOT NULL AND accept_deadline IS NULL
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS staffing_request (
                  request_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  province_id INT NOT NULL, city_id INT NOT NULL,
                  source_af_id INT NULL,
                  requested_count INT NOT NULL DEFAULT 1,
                  reason VARCHAR(300) NOT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                  requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  decided_at DATETIME NULL, decision_remark VARCHAR(300) NULL,
                  PRIMARY KEY (request_id),
                  KEY idx_staffing_region_status (province_id, city_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                COMMENT='属地网格员增配申请'
                """);
        addColumnIfMissing("staffing_request", "fulfilled_member_id",
                "VARCHAR(20) NULL COMMENT '审批通过后创建的网格员编号'");
        addColumnIfMissing("staffing_request", "provisioned_at",
                "DATETIME NULL COMMENT '网格员实际增配时间'");
        addIndexIfMissing("aqi_feedback", "idx_feedback_region_date", "province_id, city_id, af_date");
        addIndexIfMissing("aqi_feedback", "idx_feedback_member_state", "gm_id, state");
        addIndexIfMissing("aqi_measurement", "idx_measurement_status_date", "record_status, measured_at");
        addIndexIfMissing("aqi_measurement", "idx_measurement_region_date", "province_code, city_code, measured_at");
        addIndexIfMissing("supervisor", "idx_supervisor_region", "province_id, city_id");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name=? AND column_name=?
                """, Integer.class, table, column);
        if (count == null || count == 0) jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private void addIndexIfMissing(String table, String index, String columns) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name=? AND index_name=?
                """, Integer.class, table, index);
        if (count == null || count == 0) jdbcTemplate.execute("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
    }
}
