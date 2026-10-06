package com.yuan.demo.config;

import com.yuan.demo.service.StaffingProvisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 兼容旧数据：补齐此前只改成“已同意”、但尚未创建网格员的申请。 */
@Component
@Order(2)
@RequiredArgsConstructor
public class StaffingProvisionRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final StaffingProvisionService staffingProvisionService;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT request_id FROM staffing_request
                WHERE status='APPROVED' AND fulfilled_member_id IS NULL
                ORDER BY request_id
                """, Long.class);
        ids.forEach(staffingProvisionService::provision);
    }
}
