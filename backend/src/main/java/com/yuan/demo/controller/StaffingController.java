package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.service.StaffingProvisionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 属地人员不足时，由管理员提出申请、决策者审批。 */
@RestController
@RequestMapping("/staffing")
@RequiredArgsConstructor
public class StaffingController {
    private final JdbcTemplate jdbcTemplate;
    private final StaffingProvisionService staffingProvisionService;

    @GetMapping("/requests")
    @RequireRole({"ADMIN", "DECISION"})
    public ResultVO<List<Map<String, Object>>> requests() {
        return new ResultVO<>(200, "查询成功", jdbcTemplate.queryForList("""
                SELECT r.request_id AS requestId,r.province_id AS provinceId,r.city_id AS cityId,
                       p.province_name AS provinceName,c.city_name AS cityName,r.source_af_id AS sourceAfId,
                       r.requested_count AS requestedCount,r.reason,r.status,
                       r.fulfilled_member_id AS fulfilledMemberId,r.provisioned_at AS provisionedAt,
                       r.requested_at AS requestedAt,r.decided_at AS decidedAt,r.decision_remark AS decisionRemark
                FROM staffing_request r
                JOIN grid_province p ON p.province_id=r.province_id
                JOIN grid_city c ON c.city_id=r.city_id AND c.province_id=r.province_id
                ORDER BY (r.status='PENDING') DESC,r.requested_at DESC
                """));
    }

    @PostMapping("/requests/{id}/decision")
    @RequireRole("DECISION")
    @Transactional
    public ResultVO<Map<String, Object>> decide(@PathVariable long id, @RequestBody DecisionRequest request) {
        String status = request.approved ? "APPROVED" : "REJECTED";
        int updated = jdbcTemplate.update("""
                UPDATE staffing_request SET status=?,decided_at=NOW(),decision_remark=?
                WHERE request_id=? AND status='PENDING'
                """, status, request.remark == null ? "" : request.remark.trim(), id);
        if (updated != 1) return new ResultVO<>(409, "申请不存在或已完成决策");
        Map<String, Object> result = request.approved
                ? staffingProvisionService.provision(id)
                : Map.of("created", false);
        return new ResultVO<>(200, request.approved ? "已同意并完成属地网格员增配" : "已驳回增配申请", result);
    }

    @Data
    public static class DecisionRequest { private boolean approved; private String remark; }
}
