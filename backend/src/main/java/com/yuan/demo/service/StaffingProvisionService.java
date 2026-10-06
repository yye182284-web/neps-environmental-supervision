package com.yuan.demo.service;

import com.yuan.demo.security.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将已批准的人员增配申请落实为可登录、可接单的属地网格员。 */
@Service
@RequiredArgsConstructor
public class StaffingProvisionService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;

    @Transactional
    public Map<String, Object> provision(long requestId) {
        Map<String, Object> request = jdbcTemplate.queryForMap("""
                SELECT r.request_id,r.province_id,r.city_id,r.requested_count,r.status,
                       r.fulfilled_member_id,c.city_name
                FROM staffing_request r
                JOIN grid_city c ON c.city_id=r.city_id AND c.province_id=r.province_id
                WHERE r.request_id=? FOR UPDATE
                """, requestId);
        if (!"APPROVED".equals(request.get("status"))) {
            throw new IllegalStateException("只有已同意的申请才能落实增配人员");
        }
        Object fulfilled = request.get("fulfilled_member_id");
        if (fulfilled != null && !fulfilled.toString().isBlank()) {
            return memberResult(fulfilled.toString(), null, false);
        }

        int provinceId = ((Number) request.get("province_id")).intValue();
        int cityId = ((Number) request.get("city_id")).intValue();
        int count = Math.max(1, Math.min(10, ((Number) request.get("requested_count")).intValue()));
        String cityName = request.get("city_name").toString();
        String firstId = null;
        String firstCode = null;
        for (int i = 1; i <= count; i++) {
            long numericId = 800_000_000L + requestId * 10 + i;
            String memberId = Long.toString(numericId);
            String memberCode = "AUTO" + requestId + (count > 1 ? String.format("%02d", i) : "");
            String phone = "19" + String.format("%09d", numericId % 1_000_000_000L);
            jdbcTemplate.update("""
                    INSERT INTO grid_member
                      (gm_id,gm_name,gm_code,password,province_id,city_id,tel,state,remarks)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, memberId, cityName + "增配网格员" + (count > 1 ? i : ""), memberCode,
                    passwordService.encode("123456"), provinceId, cityId, phone, 0,
                    "由人员增配申请 #" + requestId + " 审批后创建");
            if (firstId == null) {
                firstId = memberId;
                firstCode = memberCode;
            }
        }
        jdbcTemplate.update("""
                UPDATE staffing_request SET fulfilled_member_id=?,provisioned_at=NOW()
                WHERE request_id=?
                """, firstId, requestId);
        return memberResult(firstId, firstCode, true);
    }

    private Map<String, Object> memberResult(String memberId, String memberCode, boolean created) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memberId", memberId);
        result.put("memberCode", memberCode);
        result.put("created", created);
        if (created) result.put("temporaryPassword", "123456");
        return result;
    }
}
