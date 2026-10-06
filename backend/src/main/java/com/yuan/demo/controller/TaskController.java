package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.entity.Aqi;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.security.SessionAuth;
import com.yuan.demo.service.IAqiService;
import com.yuan.demo.service.TaskLeaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** AQI 检测任务的属地优先指派与最终实测结果提交。 */
@RestController
@RequestMapping("/task")
@CrossOrigin
@RequiredArgsConstructor
@Tag(name = "AQI 检测任务", description = "按省市属地优先指派任务，网格员提交实测后直接形成最终结果")
public class TaskController {

    private final JdbcTemplate jdbcTemplate;
    private final IAqiService aqiService;
    private final TaskLeaseService taskLeaseService;
    @Value("${neps.task.accept-timeout-minutes:30}")
    private int acceptTimeoutMinutes;

    @GetMapping("/available-members")
    @RequireRole("ADMIN")
    public ResultVO<List<Map<String, Object>>> availableMembers(
            @RequestParam(required = false) Integer provinceId,
            @RequestParam(required = false) Integer cityId) {
        taskLeaseService.releaseExpiredAssignments();
        StringBuilder sql = new StringBuilder("""
                SELECT gm.gm_id AS `gmId`, gm.gm_name AS `gmName`, gm.gm_code AS `gmCode`,
                       gm.province_id AS `provinceId`, gm.city_id AS `cityId`, gm.tel, gm.state,
                       SUM(CASE WHEN f.state IN (1, 2) THEN 1 ELSE 0 END) AS `currentTaskCount`,
                       MAX(CONCAT(f.assign_date, ' ', f.assign_time)) AS `lastAssignedAt`
                FROM grid_member gm
                LEFT JOIN aqi_feedback f ON f.gm_id = CAST(gm.gm_id AS UNSIGNED)
                WHERE gm.state = 0
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (provinceId != null && cityId != null) { sql.append(" AND gm.province_id=? AND gm.city_id=? "); args.add(provinceId); args.add(cityId); }
        else if (provinceId != null) { sql.append(" AND gm.province_id=? "); args.add(provinceId); }
        sql.append("""
                GROUP BY gm.gm_id, gm.gm_name, gm.gm_code, gm.province_id, gm.city_id,
                         gm.tel, gm.state
                ORDER BY `currentTaskCount`, `lastAssignedAt`, gm.gm_name
                """);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return new ResultVO<>(200, cityId == null ? "查询成功" : "仅返回同城可用网格员", rows);
    }

    @GetMapping("/grid/{gmId}")
    @RequireRole("GRID_MEMBER")
    public ResultVO<List<Map<String, Object>>> gridTasks(@PathVariable String gmId,
                                                         HttpServletRequest servletRequest) {
        taskLeaseService.releaseExpiredAssignments();
        if (!gmId.equals(SessionAuth.subjectId(servletRequest))) {
            return new ResultVO<>(403, "只能查看分配给自己的任务");
        }
        long memberId = numericMemberId(gmId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT f.af_id AS `afId`, f.tel_id AS `telId`, f.province_id AS `provinceId`,
                       p.province_name AS `provinceName`, f.city_id AS `cityId`,
                       c.city_name AS `cityName`, f.address, f.information,
                       f.estimated_grade AS `estimatedGrade`, f.af_date AS `afDate`,
                       f.af_time AS `afTime`, f.assign_date AS `assignDate`,
                       f.assign_time AS `assignTime`, f.accepted_at AS `acceptedAt`,
                       f.accept_deadline AS `acceptDeadline`, f.state,
                       m.measurement_id AS `measurementId`, m.record_status AS `recordStatus`,
                       m.so2_concentration AS `so2Concentration`,
                       m.co_concentration AS `coConcentration`,
                       m.pm25_concentration AS `pm25Concentration`,
                       m.so2_level_id AS `so2LevelId`, m.co_level_id AS `coLevelId`,
                       m.pm25_level_id AS `pm25LevelId`, m.overall_level_id AS `overallLevelId`,
                       m.measured_at AS `measuredAt`
                FROM aqi_feedback f
                LEFT JOIN grid_province p ON p.province_id = f.province_id
                LEFT JOIN grid_city c ON c.city_id = f.city_id AND c.province_id = f.province_id
                LEFT JOIN aqi_measurement m ON m.task_id = f.af_id
                WHERE f.gm_id = ?
                ORDER BY f.af_date DESC, f.af_time DESC, f.af_id DESC
                """, memberId);
        return new ResultVO<>(200, "查询成功", rows);
    }

    @GetMapping("/{afId:\\d+}")
    @RequireRole({"GRID_MEMBER", "ADMIN", "DECISION"})
    public ResultVO<Map<String, Object>> taskDetail(@PathVariable Integer afId,
                                                    HttpServletRequest servletRequest) {
        Map<String, Object> detail = taskDetailData(afId);
        if (detail == null) return new ResultVO<>(404, "未找到该任务");
        if (SessionAuth.hasRole(servletRequest, "GRID_MEMBER")) {
            Object member = detail.get("gm_id");
            if (member == null || !member.toString().equals(SessionAuth.subjectId(servletRequest))) {
                return new ResultVO<>(403, "只能查看分配给自己的任务");
            }
        }
        return new ResultVO<>(200, "查询成功", detail);
    }

    private Map<String, Object> taskDetailData(Integer afId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT f.*, p.province_name AS `provinceName`, c.city_name AS `cityName`,
                       gm.gm_name AS `gmName`, gm.gm_code AS `gmCode`,
                       m.measurement_id AS `measurementId`, m.measured_at AS `measuredAt`,
                       m.so2_concentration AS `so2Concentration`,
                       m.co_concentration AS `coConcentration`,
                       m.pm25_concentration AS `pm25Concentration`,
                       m.so2_level_id AS `so2LevelId`, m.co_level_id AS `coLevelId`,
                       m.pm25_level_id AS `pm25LevelId`,
                       m.overall_level_id AS `overallLevelId`,
                       m.record_status AS `recordStatus`, m.remark AS `measurementRemark`
                FROM aqi_feedback f
                LEFT JOIN grid_province p ON p.province_id = f.province_id
                LEFT JOIN grid_city c ON c.city_id = f.city_id AND c.province_id = f.province_id
                LEFT JOIN grid_member gm ON CAST(gm.gm_id AS UNSIGNED) = f.gm_id
                LEFT JOIN aqi_measurement m ON m.task_id = f.af_id
                WHERE f.af_id = ?
                """, afId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @PostMapping("/assign")
    @RequireRole("ADMIN")
    @Transactional
    public ResultVO<Boolean> assign(@RequestBody AssignRequest request) {
        taskLeaseService.releaseExpiredAssignments();
        if (request.getAfId() == null) {
            throw new IllegalArgumentException("反馈编号不能为空");
        }
        List<Map<String, Object>> feedbackRows = jdbcTemplate.queryForList(
                "SELECT province_id, city_id FROM aqi_feedback WHERE af_id = ?", request.getAfId());
        if (feedbackRows.isEmpty()) return new ResultVO<>(404, "未找到该反馈", false);
        Map<String, Object> feedback = feedbackRows.get(0);
        String selectedMemberId = request.getGmId();
        if (selectedMemberId == null || selectedMemberId.isBlank()) {
            List<Map<String, Object>> recommended = availableMembers(
                    ((Number) feedback.get("province_id")).intValue(),
                    ((Number) feedback.get("city_id")).intValue()).getData();
            if (recommended.isEmpty()) {
                boolean created = createStaffingRequest(request.getAfId(), feedback,
                        "同市没有可用网格员，任务无法属地化指派");
                return new ResultVO<>(202, created
                        ? "增配申请已提交给决策者"
                        : "该地区已有待决策的增配申请，请勿重复提交", true);
            }
            selectedMemberId = recommended.get(0).get("gmId").toString();
        }
        long memberId = numericMemberId(selectedMemberId);
        Integer memberCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM grid_member
                WHERE gm_id=? AND state=0 AND province_id=? AND city_id=?
                """, Integer.class, selectedMemberId, feedback.get("province_id"), feedback.get("city_id"));
        if (memberCount == null || memberCount == 0) {
            boolean created = createStaffingRequest(request.getAfId(), feedback,
                    "管理员选择的网格员不属于任务所在城市");
            return new ResultVO<>(202, created
                    ? "禁止跨地区指派，增配申请已提交给决策者"
                    : "禁止跨地区指派；该地区已有待决策的增配申请", true);
        }
        int updated = jdbcTemplate.update("""
                UPDATE aqi_feedback
                SET gm_id = ?, assign_date = ?, assign_time = ?, state = 1,
                    accepted_at=NULL, accept_deadline=DATE_ADD(NOW(), INTERVAL ? MINUTE)
                WHERE af_id = ? AND state IN (0, 1)
                """, memberId, LocalDate.now().toString(),
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), acceptTimeoutMinutes, request.getAfId());
        if (updated == 0) {
            return new ResultVO<>(400, "反馈不存在或已经进入实测流程", false);
        }
        return new ResultVO<>(200, "任务指派成功", true);
    }

    @PostMapping("/accept")
    @RequireRole("GRID_MEMBER")
    @Transactional
    public ResultVO<Boolean> accept(@RequestBody AssignRequest request, HttpServletRequest servletRequest) {
        taskLeaseService.releaseExpiredAssignments();
        long gmId = numericMemberId(SessionAuth.subjectId(servletRequest));
        int updated = jdbcTemplate.update("""
                UPDATE aqi_feedback SET state=2, accepted_at=NOW()
                WHERE af_id=? AND gm_id=? AND state=1 AND accept_deadline>=NOW()
                """, request.getAfId(), gmId);
        if (updated == 0) return new ResultVO<>(409, "任务已超时回池、已被处理或不属于当前网格员", false);
        return new ResultVO<>(200, "接单成功，请及时完成现场检测", true);
    }

    @PostMapping("/measure")
    @RequireRole("GRID_MEMBER")
    @Transactional
    public ResultVO<Map<String, Object>> measure(@RequestBody MeasureRequest request,
                                                 HttpServletRequest servletRequest) {
        request.setGmId(SessionAuth.subjectId(servletRequest));
        validateMeasurement(request);
        long memberId = numericMemberId(request.getGmId());
        List<Map<String, Object>> feedbackRows = jdbcTemplate.queryForList(
                "SELECT * FROM aqi_feedback WHERE af_id = ?", request.getAfId());
        if (feedbackRows.isEmpty()) return new ResultVO<>(404, "未找到该任务");
        Map<String, Object> feedback = feedbackRows.get(0);
        Object assigned = feedback.get("gm_id");
        if (assigned == null || Long.parseLong(assigned.toString()) != memberId) {
            return new ResultVO<>(403, "该任务未指派给当前网格员");
        }
        if (((Number) feedback.get("state")).intValue() != 2) {
            return new ResultVO<>(400, "请先接单再提交实测数据，或任务已经完成");
        }

        int so2Level = levelFor(request.getSo2Concentration(), "so2");
        int coLevel = levelFor(request.getCoConcentration(), "co");
        int pm25Level = levelFor(request.getPm25Concentration(), "spm");
        Map<String, Object> member = jdbcTemplate.queryForMap(
                "SELECT gm_code, province_id, city_id FROM grid_member WHERE gm_id = ?", request.getGmId());
        String gridCode = member.get("gm_code") == null ? request.getGmId() : member.get("gm_code").toString();
        String provinceCode = String.format("%06d", ((Number) feedback.get("province_id")).intValue());
        String cityCode = String.format("%06d", ((Number) feedback.get("city_id")).intValue());
        String address = request.getDetailAddress() == null || request.getDetailAddress().isBlank()
                ? String.valueOf(feedback.get("address")) : request.getDetailAddress().trim();

        jdbcTemplate.update("""
                INSERT INTO aqi_measurement
                  (task_id, grid_code, province_code, city_code, detail_address, inspector_id,
                   measured_at, so2_concentration, co_concentration, pm25_concentration,
                  so2_level_id, co_level_id, pm25_level_id, record_status, submitted_at, confirmed_at, remark)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, 'CONFIRMED', NOW(), NOW(), ?)
                ON DUPLICATE KEY UPDATE
                  grid_code = VALUES(grid_code), detail_address = VALUES(detail_address),
                  inspector_id = VALUES(inspector_id), measured_at = NOW(),
                  so2_concentration = VALUES(so2_concentration),
                  co_concentration = VALUES(co_concentration),
                  pm25_concentration = VALUES(pm25_concentration),
                  so2_level_id = VALUES(so2_level_id), co_level_id = VALUES(co_level_id),
                  pm25_level_id = VALUES(pm25_level_id), record_status = 'CONFIRMED',
                  submitted_at = NOW(), confirmed_by = NULL, confirmed_at = NOW(),
                  remark = VALUES(remark)
                """, request.getAfId(), gridCode, provinceCode, cityCode, address, memberId,
                request.getSo2Concentration(), request.getCoConcentration(), request.getPm25Concentration(),
                so2Level, coLevel, pm25Level, request.getRemark());
        jdbcTemplate.update("UPDATE aqi_feedback SET state = 3 WHERE af_id = ?", request.getAfId());
        return new ResultVO<>(200, "实测数据已保存为最终结果", taskDetailData(request.getAfId()));
    }

    /** 管理端和决策端查看已经成为最终结果的实测记录。 */
    @GetMapping("/measurements")
    @RequireRole({"ADMIN", "DECISION"})
    public ResultVO<List<Map<String, Object>>> measurements() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT m.measurement_id AS `measurementId`, m.task_id AS `afId`,
                       m.measured_at AS `measuredAt`, m.so2_concentration AS `so2Concentration`,
                       m.co_concentration AS `coConcentration`, m.pm25_concentration AS `pm25Concentration`,
                       m.overall_level_id AS `overallLevelId`, m.is_exceeded AS `isExceeded`,
                       m.record_status AS `recordStatus`, m.remark,
                       f.address, f.information, p.province_name AS `provinceName`,
                       c.city_name AS `cityName`, gm.gm_name AS `gmName`
                FROM aqi_measurement m
                JOIN aqi_feedback f ON f.af_id = m.task_id
                LEFT JOIN grid_province p ON p.province_id = f.province_id
                LEFT JOIN grid_city c ON c.city_id = f.city_id AND c.province_id = f.province_id
                LEFT JOIN grid_member gm ON CAST(gm.gm_id AS UNSIGNED) = m.inspector_id
                WHERE m.record_status = 'CONFIRMED'
                ORDER BY m.measured_at DESC
                """);
        return new ResultVO<>(200, "查询成功", rows);
    }

    /** 管理员按区域、等级、日期和关键字分页查询最终实测结果。 */
    @GetMapping("/measurements/query")
    @RequireRole({"ADMIN", "DECISION"})
    public ResultVO<Map<String, Object>> queryMeasurements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer provinceId,
            @RequestParam(required = false) Integer cityId,
            @RequestParam(required = false) Integer levelId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        StringBuilder where = new StringBuilder(" WHERE m.record_status = 'CONFIRMED' ");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (f.address LIKE ? OR f.information LIKE ? OR gm.gm_name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (provinceId != null) { where.append(" AND f.province_id = ? "); args.add(provinceId); }
        if (cityId != null) { where.append(" AND f.city_id = ? "); args.add(cityId); }
        if (levelId != null) { where.append(" AND m.overall_level_id = ? "); args.add(levelId); }
        if (startDate != null && !startDate.isBlank()) { LocalDate.parse(startDate); where.append(" AND DATE(m.measured_at) >= ? "); args.add(startDate); }
        if (endDate != null && !endDate.isBlank()) { LocalDate.parse(endDate); where.append(" AND DATE(m.measured_at) <= ? "); args.add(endDate); }
        String from = " FROM aqi_measurement m JOIN aqi_feedback f ON f.af_id=m.task_id " +
                "LEFT JOIN grid_member gm ON CAST(gm.gm_id AS UNSIGNED)=m.inspector_id " +
                "LEFT JOIN grid_province p ON p.province_id=f.province_id " +
                "LEFT JOIN grid_city c ON c.city_id=f.city_id AND c.province_id=f.province_id ";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        java.util.ArrayList<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(safeSize); pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT m.measurement_id AS `measurementId`, m.task_id AS `afId`, m.measured_at AS `measuredAt`,
                       m.so2_concentration AS `so2Concentration`, m.co_concentration AS `coConcentration`,
                       m.pm25_concentration AS `pm25Concentration`, m.so2_level_id AS `so2LevelId`,
                       m.co_level_id AS `coLevelId`, m.pm25_level_id AS `pm25LevelId`,
                       m.overall_level_id AS `overallLevelId`, m.is_exceeded AS `isExceeded`, m.remark,
                       f.address, f.information, p.province_name AS `provinceName`,
                       c.city_name AS `cityName`, gm.gm_name AS `gmName`
                """ + from + where + " ORDER BY m.measured_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("records", records); result.put("total", total == null ? 0 : total);
        result.put("page", safePage); result.put("size", safeSize);
        return new ResultVO<>(200, "查询成功", result);
    }

    private long numericMemberId(String gmId) {
        try {
            return Long.parseLong(gmId);
        } catch (Exception e) {
            throw new IllegalArgumentException("网格员编号必须为数字");
        }
    }

    private boolean createStaffingRequest(Integer afId, Map<String, Object> feedback, String reason) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO staffing_request(province_id,city_id,source_af_id,requested_count,reason,status)
                SELECT ?,?,?,?,?, 'PENDING' FROM DUAL
                WHERE NOT EXISTS (SELECT 1 FROM staffing_request
                                  WHERE province_id=? AND city_id=? AND status='PENDING')
                """, feedback.get("province_id"), feedback.get("city_id"), afId, 1, reason,
                feedback.get("province_id"), feedback.get("city_id"));
        return inserted == 1;
    }

    private void validateMeasurement(MeasureRequest request) {
        if (request == null || request.getAfId() == null || request.getGmId() == null) {
            throw new IllegalArgumentException("任务编号和网格员编号不能为空");
        }
        if (request.getSo2Concentration() == null || request.getCoConcentration() == null
                || request.getPm25Concentration() == null) {
            throw new IllegalArgumentException("SO2、CO 和 PM2.5 实测值不能为空");
        }
        if (request.getSo2Concentration().signum() < 0 || request.getCoConcentration().signum() < 0
                || request.getPm25Concentration().signum() < 0) {
            throw new IllegalArgumentException("污染物实测值不能小于 0");
        }
    }

    private int levelFor(BigDecimal value, String pollutant) {
        List<Aqi> levels = aqiService.list().stream()
                .sorted(Comparator.comparing(Aqi::getAqiId)).toList();
        if (levels.isEmpty()) throw new IllegalStateException("AQI 等级规则尚未配置");
        for (Aqi level : levels) {
            Integer max = switch (pollutant) {
                case "so2" -> level.getSo2Max();
                case "co" -> level.getCoMax();
                default -> level.getSpmMax();
            };
            if (max == null || value.compareTo(BigDecimal.valueOf(max)) <= 0) {
                return level.getAqiId();
            }
        }
        return levels.get(levels.size() - 1).getAqiId();
    }

    @Data
    public static class AssignRequest {
        private Integer afId;
        private String gmId;
    }

    @Data
    public static class MeasureRequest {
        private Integer afId;
        private String gmId;
        private BigDecimal so2Concentration;
        private BigDecimal coConcentration;
        private BigDecimal pm25Concentration;
        private String detailAddress;
        private String remark;
    }
}
