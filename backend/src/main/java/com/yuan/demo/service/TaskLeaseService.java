package com.yuan.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理任务接单时限；超时未接单的任务自动释放回属地待指派池。 */
@Service
@RequiredArgsConstructor
public class TaskLeaseService {
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    @Transactional
    public void releaseExpiredAssignments() {
        jdbcTemplate.update("""
                UPDATE aqi_feedback
                SET gm_id=NULL, assign_date=NULL, assign_time=NULL, state=0,
                    accepted_at=NULL, accept_deadline=NULL,
                    remarks=CONCAT(COALESCE(remarks,''), CASE WHEN remarks IS NULL OR remarks='' THEN '' ELSE '；' END,
                                   '网格员超时未接单，任务已自动回池')
                WHERE state=1 AND accept_deadline IS NOT NULL AND accept_deadline < NOW()
                  AND NOT EXISTS (SELECT 1 FROM aqi_measurement m WHERE m.task_id=aqi_feedback.af_id)
                """);
    }
}
