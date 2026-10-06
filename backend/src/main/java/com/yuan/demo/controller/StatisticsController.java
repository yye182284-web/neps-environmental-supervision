package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.security.RequireRole;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** 系统管理员和决策者共用的统计数据接口。 */
@RestController
@RequestMapping("/statistics")
@CrossOrigin
@AllArgsConstructor
@Tag(name = "环境统计分析", description = "管理员和决策者统计看板数据")
@RequireRole({"ADMIN", "DECISION"})
public class StatisticsController {

    private final JdbcTemplate jdbcTemplate;
    private static final long DASHBOARD_CACHE_MILLIS = 60_000L;
    private static volatile Map<String, Object> dashboardCache;
    private static volatile long dashboardCacheTime;

    /** 管理员首页只需要四个任务数字，不再执行完整决策看板的十多组聚合查询。 */
    @GetMapping("/overview")
    public ResultVO<Map<String, Object>> overviewData() {
        return new ResultVO<>(200, "查询成功", overview());
    }

    @GetMapping("/dashboard")
    public ResultVO<Map<String, Object>> dashboard(
            @RequestParam(defaultValue = "false") boolean refresh) {
        long now = System.currentTimeMillis();
        Map<String, Object> snapshot = dashboardCache;
        if (!refresh && snapshot != null && now - dashboardCacheTime < DASHBOARD_CACHE_MILLIS) {
            return new ResultVO<>(200, "查询成功", snapshot);
        }
        synchronized (StatisticsController.class) {
            now = System.currentTimeMillis();
            snapshot = dashboardCache;
            if (!refresh && snapshot != null && now - dashboardCacheTime < DASHBOARD_CACHE_MILLIS) {
                return new ResultVO<>(200, "查询成功", snapshot);
            }
            snapshot = buildDashboard();
            dashboardCache = snapshot;
            dashboardCacheTime = System.currentTimeMillis();
            return new ResultVO<>(200, "查询成功", snapshot);
        }
    }

    private Map<String, Object> buildDashboard() {
        CompletableFuture<Map<String, Object>> overview = async("overview", this::overview);
        CompletableFuture<Map<String, Object>> measurement = async("measurement", this::measurement);
        CompletableFuture<List<Map<String, Object>>> province = async("province", this::province);
        CompletableFuture<List<Map<String, Object>>> cityRanking = async("cityRanking", this::cityRanking);
        CompletableFuture<List<Map<String, Object>>> distribution = async("distribution", this::distribution);
        CompletableFuture<List<Map<String, Object>>> trend = async("trend", this::trend);
        CompletableFuture<Map<String, Object>> efficiency = async("efficiency", this::efficiency);
        CompletableFuture<List<Map<String, Object>>> gridPerformance = async("gridPerformance", this::gridPerformance);
        CompletableFuture<List<Map<String, Object>>> recentMeasurements = async("recentMeasurements", this::recentMeasurements);
        CompletableFuture<Map<String, Object>> risk = async("risk", this::risk);
        CompletableFuture<Map<String, Object>> workforce = async("workforce", this::workforce);
        CompletableFuture<List<Map<String, Object>>> yearComparison = async("yearComparison", this::yearComparison);
        CompletableFuture<List<Map<String, Object>>> cityComparison = async("cityComparison", this::cityComparison);
        CompletableFuture<List<Map<String, Object>>> reportComparison = async("reportComparison", this::reportComparison);
        CompletableFuture<List<Map<String, Object>>> staffingRequests = async("staffingRequests", this::staffingRequests);

        CompletableFuture.allOf(overview, measurement, province, cityRanking, distribution, trend,
                efficiency, gridPerformance, recentMeasurements, risk, workforce, yearComparison,
                cityComparison, reportComparison, staffingRequests).join();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", overview.join());
        data.put("measurement", measurement.join());
        data.put("province", province.join());
        data.put("cityRanking", cityRanking.join());
        data.put("distribution", distribution.join());
        data.put("trend", trend.join());
        data.put("efficiency", efficiency.join());
        data.put("gridPerformance", gridPerformance.join());
        data.put("recentMeasurements", recentMeasurements.join());
        data.put("risk", risk.join());
        data.put("workforce", workforce.join());
        data.put("yearComparison", yearComparison.join());
        data.put("cityComparison", cityComparison.join());
        data.put("reportComparison", reportComparison.join());
        data.put("staffingRequests", staffingRequests.join());
        return data;
    }

    private <T> CompletableFuture<T> async(String name, Supplier<T> query) {
        return CompletableFuture.supplyAsync(query);
    }

    private Map<String, Object> measurement() {
        return jdbcTemplate.queryForMap("""
                SELECT SUM(record_status = 'CONFIRMED') AS `measurementTotal`,
                       SUM(record_status = 'SUBMITTED') AS `pendingTotal`,
                       SUM(record_status = 'CONFIRMED') AS `confirmedTotal`,
                       SUM(record_status = 'CONFIRMED' AND is_exceeded = 0) AS `goodTotal`,
                       SUM(record_status = 'CONFIRMED' AND is_exceeded = 1) AS `pollutionTotal`,
                       COUNT(DISTINCT CASE WHEN record_status = 'CONFIRMED' THEN province_code END) AS `provinceCovered`,
                       COUNT(DISTINCT CASE WHEN record_status = 'CONFIRMED' THEN city_code END) AS `cityCovered`,
                       (SELECT COUNT(*) FROM grid_province) AS `provinceTotal`,
                       (SELECT COUNT(*) FROM grid_city) AS `cityTotal`,
                       ROUND(100 * COUNT(DISTINCT CASE WHEN record_status = 'CONFIRMED' THEN province_code END)
                           / NULLIF((SELECT COUNT(*) FROM grid_province), 0), 2) AS `provinceCoverageRate`,
                       ROUND(100 * COUNT(DISTINCT CASE WHEN record_status = 'CONFIRMED' THEN city_code END)
                           / NULLIF((SELECT COUNT(*) FROM grid_city), 0), 2) AS `cityCoverageRate`
                FROM aqi_measurement
                """);
    }

    private Map<String, Object> overview() {
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS `feedbackTotal`,
                       SUM(state = 0) AS `unassignedTotal`,
                       SUM(state IN (1, 2)) AS `processingTotal`,
                       SUM(state = 3) AS `completedTotal`
                FROM aqi_feedback
                """);
    }

    private Map<String, Object> workforce() {
        Map<String, Object> result = new LinkedHashMap<>(jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS `total`,
                       SUM(gm.state = 0 AND EXISTS (
                           SELECT 1 FROM aqi_feedback f WHERE f.gm_id=CAST(gm.gm_id AS UNSIGNED) AND f.state IN (1,2)
                       )) AS `busy`,
                       SUM(gm.state = 0 AND NOT EXISTS (
                           SELECT 1 FROM aqi_feedback f WHERE f.gm_id=CAST(gm.gm_id AS UNSIGNED) AND f.state IN (1,2)
                       )) AS `idle`,
                       SUM(gm.state <> 0) AS `unavailable`
                FROM grid_member gm
                """));
        Number pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staffing_request WHERE status='PENDING'", Number.class);
        long total = number(result.get("total"));
        long idle = number(result.get("idle"));
        long busy = number(result.get("busy"));
        long pendingCount = pending == null ? 0 : pending.longValue();
        result.put("pendingRequests", pendingCount);
        result.put("needMore", pendingCount > 0 || (total > 0 && idle == 0 && busy == total));
        result.put("recommendation", pendingCount > 0
                ? "存在本地人员增配申请，建议结合地区任务量审批补充网格员。"
                : idle == 0 && busy > 0 ? "当前全部可用网格员均在执行任务，建议增加备用人员。"
                : "当前人员配置可满足任务处理需求。" );
        return result;
    }

    private List<Map<String, Object>> yearComparison() {
        return jdbcTemplate.queryForList("""
                SELECT YEAR(m.measured_at) AS `year`, COUNT(*) AS `measurementTotal`,
                       ROUND(AVG(m.overall_level_id),2) AS `averageLevel`,
                       SUM(m.overall_level_id >= 3) AS `pollutionTotal`,
                       ROUND(100 * SUM(m.overall_level_id >= 3) / COUNT(*),2) AS `pollutionRate`,
                       (SELECT COUNT(*) FROM aqi_feedback f WHERE YEAR(f.af_date)=YEAR(m.measured_at)) AS `feedbackTotal`
                FROM aqi_measurement m
                WHERE m.record_status='CONFIRMED'
                  AND YEAR(m.measured_at) IN (YEAR(CURDATE())-1, YEAR(CURDATE())-2)
                GROUP BY YEAR(m.measured_at) ORDER BY `year` DESC
                """);
    }

    private List<Map<String, Object>> cityComparison() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.province_name AS `provinceName`, c.city_name AS `cityName`,
                       COUNT(CASE WHEN YEAR(m.measured_at)=YEAR(CURDATE())-2 THEN 1 END) AS `previousMeasurementTotal`,
                       COUNT(CASE WHEN YEAR(m.measured_at)=YEAR(CURDATE())-1 THEN 1 END) AS `currentMeasurementTotal`,
                       ROUND(AVG(CASE WHEN YEAR(m.measured_at)=YEAR(CURDATE())-2 THEN m.overall_level_id END),2) AS `previousAverageLevel`,
                       ROUND(AVG(CASE WHEN YEAR(m.measured_at)=YEAR(CURDATE())-1 THEN m.overall_level_id END),2) AS `currentAverageLevel`,
                       ROUND(100 * SUM(YEAR(m.measured_at)=YEAR(CURDATE())-1 AND m.overall_level_id>=3)
                           / NULLIF(SUM(YEAR(m.measured_at)=YEAR(CURDATE())-1),0),2) AS `currentPollutionRate`
                FROM grid_city c JOIN grid_province p ON p.province_id=c.province_id
                LEFT JOIN aqi_measurement m ON m.province_code=LPAD(c.province_id,6,'0')
                    AND m.city_code=LPAD(c.city_id,6,'0') AND m.record_status='CONFIRMED'
                    AND YEAR(m.measured_at) IN (YEAR(CURDATE())-1,YEAR(CURDATE())-2)
                GROUP BY p.province_name,c.city_name
                HAVING previousMeasurementTotal>0 OR currentMeasurementTotal>0
                ORDER BY currentAverageLevel DESC
                """);
        for (Map<String, Object> row : rows) {
            double previous = decimal(row.get("previousAverageLevel"));
            double current = decimal(row.get("currentAverageLevel"));
            double change = Math.round((current - previous) * 100.0) / 100.0;
            row.put("levelChange", change);
            row.put("assessment", current >= 4 ? "污染较重"
                    : current <= 1.8 ? "治理较好"
                    : change <= -0.5 ? "明显改善"
                    : change >= 0.5 ? "污染上升" : "总体平稳");
            row.put("governanceAdvice", current >= 4
                    ? "重点排查工业排放、交通源和施工扬尘，增加高风险时段复测。"
                    : change >= 0.5 ? "同比污染等级上升，应开展污染源溯源并制定专项削减措施。"
                    : current <= 1.8 ? "保持现有治理措施与常态化监测，防止污染反弹。"
                    : "持续开展重点点位巡查，结合气象条件动态调整治理安排。" );
        }
        return rows;
    }

    private List<Map<String, Object>> reportComparison() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.province_name AS `provinceName`,c.city_name AS `cityName`,
                       SUM(YEAR(f.af_date)=YEAR(CURDATE())-2) AS `previousReportTotal`,
                       SUM(YEAR(f.af_date)=YEAR(CURDATE())-1) AS `currentReportTotal`,
                       COUNT(DISTINCT CASE WHEN YEAR(f.af_date)=YEAR(CURDATE())-1 THEN f.tel_id END) AS `activeSupervisorTotal`,
                       COALESCE(s.supervisor_total,0) AS `supervisorTotal`,
                       COALESCE(mx.pollution_rate,0) AS `pollutionRate`
                FROM grid_city c JOIN grid_province p ON p.province_id=c.province_id
                LEFT JOIN (
                    SELECT province_id,city_id,COUNT(*) AS supervisor_total
                    FROM supervisor GROUP BY province_id,city_id
                ) s ON s.province_id=c.province_id AND s.city_id=c.city_id
                LEFT JOIN (
                    SELECT CAST(province_code AS UNSIGNED) AS province_id,
                           CAST(city_code AS UNSIGNED) AS city_id,
                           ROUND(100*SUM(overall_level_id>=3)/NULLIF(COUNT(*),0),2) AS pollution_rate
                    FROM aqi_measurement
                    WHERE record_status='CONFIRMED'
                      AND measured_at>=MAKEDATE(YEAR(CURDATE())-1,1)
                      AND measured_at<MAKEDATE(YEAR(CURDATE()),1)
                    GROUP BY province_code,city_code
                ) mx ON mx.province_id=c.province_id AND mx.city_id=c.city_id
                LEFT JOIN aqi_feedback f ON f.province_id=c.province_id AND f.city_id=c.city_id
                    AND f.af_date>=MAKEDATE(YEAR(CURDATE())-2,1)
                    AND f.af_date<MAKEDATE(YEAR(CURDATE()),1)
                GROUP BY c.province_id,c.city_id,p.province_name,c.city_name,s.supervisor_total,mx.pollution_rate
                HAVING previousReportTotal>0 OR currentReportTotal>0
                ORDER BY currentReportTotal DESC
                """);
        double averageReports = rows.stream().mapToLong(row -> number(row.get("currentReportTotal"))).average().orElse(0);
        for (Map<String, Object> row : rows) {
            long current = number(row.get("currentReportTotal"));
            long previous = number(row.get("previousReportTotal"));
            long supervisors = number(row.get("supervisorTotal"));
            double pollutionRate = decimal(row.get("pollutionRate"));
            row.put("reportChangeRate", previous == 0 ? 0
                    : Math.round((current - previous) * 10000.0 / previous) / 100.0);
            if (current < averageReports * 0.8) {
                row.put("volumeAssessment", "报告较少");
                row.put("reasonAssessment", pollutionRate <= 25
                        ? "实测污染率较低，报告较少主要反映当地环境质量较好。"
                        : supervisors <= 3
                        ? "污染率不低但监督员数量偏少，存在监督覆盖不足风险，建议增配质量监督员。"
                        : "建议核查公众参与度、巡查频率和反馈渠道可用性。" );
            } else if (current > averageReports * 1.2) {
                row.put("volumeAssessment", "报告较多");
                row.put("reasonAssessment", pollutionRate >= 50
                        ? "报告量与较高污染率一致，应优先安排治理和复测。"
                        : "报告活跃但实测污染率不高，应核查高频反馈点并加强公众解释。" );
            } else {
                row.put("volumeAssessment", "报告量正常");
                row.put("reasonAssessment", "监督覆盖和报告数量处于区域平均水平。" );
            }
        }
        return rows;
    }

    private List<Map<String, Object>> staffingRequests() {
        return jdbcTemplate.queryForList("""
                SELECT r.request_id AS `requestId`,p.province_name AS `provinceName`,c.city_name AS `cityName`,
                       r.source_af_id AS `sourceAfId`,r.requested_count AS `requestedCount`,r.reason,r.status,
                       r.requested_at AS `requestedAt`,r.decision_remark AS `decisionRemark`
                FROM staffing_request r
                LEFT JOIN grid_province p ON p.province_id=r.province_id
                LEFT JOIN grid_city c ON c.city_id=r.city_id AND c.province_id=r.province_id
                ORDER BY (r.status='PENDING') DESC,r.requested_at DESC LIMIT 30
                """);
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private double decimal(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0D;
    }

    private List<Map<String, Object>> province() {
        return jdbcTemplate.queryForList("""
                SELECT p.province_id AS `provinceId`, p.province_name AS `provinceName`,
                       COUNT(DISTINCT f.af_id) AS `feedbackTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' THEN m.measurement_id END) AS `measurementTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' THEN m.measurement_id END) AS `confirmedTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' AND m.so2_level_id >= 3 THEN m.measurement_id END) AS `so2ExceedTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' AND m.co_level_id >= 3 THEN m.measurement_id END) AS `coExceedTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' AND m.pm25_level_id >= 3 THEN m.measurement_id END) AS `pm25ExceedTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' AND m.is_exceeded = 1 THEN m.measurement_id END) AS `aqiExceedTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' AND m.is_exceeded = 1 THEN m.measurement_id END) AS `pollutionTotal`
                FROM grid_province p
                LEFT JOIN aqi_feedback f ON f.province_id = p.province_id
                LEFT JOIN aqi_measurement m ON m.task_id = f.af_id
                GROUP BY p.province_id, p.province_name
                HAVING feedbackTotal > 0 OR measurementTotal > 0
                ORDER BY measurementTotal DESC, feedbackTotal DESC, p.province_id
                """);
    }

    private List<Map<String, Object>> distribution() {
        return jdbcTemplate.queryForList("""
                SELECT a.aqi_id AS `levelId`, a.chinese_explain AS `levelName`,
                       a.aqi_explain AS `quality`, a.color,
                       COUNT(m.measurement_id) AS `value`
                FROM aqi a
                LEFT JOIN aqi_measurement m ON m.overall_level_id = a.aqi_id
                                           AND m.record_status = 'CONFIRMED'
                GROUP BY a.aqi_id, a.chinese_explain, a.aqi_explain, a.color
                ORDER BY a.aqi_id
                """);
    }

    private List<Map<String, Object>> trend() {
        return jdbcTemplate.queryForList("""
                WITH RECURSIVE months AS (
                    SELECT DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 11 MONTH), '%Y-%m-01') AS month_start
                    UNION ALL
                    SELECT DATE_FORMAT(DATE_ADD(month_start, INTERVAL 1 MONTH), '%Y-%m-01')
                    FROM months WHERE month_start < DATE_FORMAT(CURDATE(), '%Y-%m-01')
                ), monthly AS (
                    SELECT mo.month_start,
                           COUNT(m.measurement_id) AS measurement_total,
                           COALESCE(ROUND(AVG(m.overall_level_id), 2), 0) AS average_level,
                           COALESCE(SUM(m.is_exceeded = 1), 0) AS pollution_total
                    FROM months mo
                    LEFT JOIN aqi_measurement m
                      ON m.record_status = 'CONFIRMED'
                     AND m.measured_at >= mo.month_start
                     AND m.measured_at < DATE_ADD(mo.month_start, INTERVAL 1 MONTH)
                    GROUP BY mo.month_start
                )
                SELECT DATE_FORMAT(month_start, '%Y-%m') AS `month`,
                       measurement_total AS `measurementTotal`, average_level AS `averageLevel`,
                       pollution_total AS `pollutionTotal`,
                       SUM(pollution_total) OVER (ORDER BY month_start) AS `cumulativePollutionTotal`
                FROM monthly ORDER BY month_start
                """);
    }

    private List<Map<String, Object>> cityRanking() {
        return jdbcTemplate.queryForList("""
                SELECT c.city_id AS `cityId`, p.province_name AS `provinceName`,
                       c.city_name AS `cityName`, COUNT(DISTINCT f.af_id) AS `feedbackTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' THEN m.measurement_id END) AS `measurementTotal`,
                       COUNT(DISTINCT CASE WHEN m.record_status = 'CONFIRMED' AND m.is_exceeded = 1 THEN m.measurement_id END) AS `pollutionTotal`
                FROM grid_city c
                JOIN grid_province p ON p.province_id = c.province_id
                LEFT JOIN aqi_feedback f ON f.city_id = c.city_id AND f.province_id = c.province_id
                LEFT JOIN aqi_measurement m ON m.task_id = f.af_id
                GROUP BY c.city_id, p.province_name, c.city_name
                HAVING feedbackTotal > 0 OR measurementTotal > 0
                ORDER BY pollutionTotal DESC, feedbackTotal DESC
                LIMIT 10
                """);
    }

    private Map<String, Object> efficiency() {
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(CASE WHEN f.gm_id IS NOT NULL AND f.gm_id <> 0 THEN 1 END) AS `assignedTotal`,
                       COUNT(CASE WHEN f.state = 3 THEN 1 END) AS `completedTotal`,
                       ROUND(AVG(CASE WHEN m.measurement_id IS NOT NULL THEN
                         TIMESTAMPDIFF(HOUR,
                           STR_TO_DATE(CONCAT(f.af_date, ' ',
                             IF(CHAR_LENGTH(f.af_time) = 5, CONCAT(f.af_time, ':00'), f.af_time)),
                             '%Y-%m-%d %H:%i:%s'), m.measured_at) END), 1) AS `averageCompletionHours`
                FROM aqi_feedback f
                LEFT JOIN aqi_measurement m ON m.task_id = f.af_id
                """);
    }

    private List<Map<String, Object>> gridPerformance() {
        return jdbcTemplate.queryForList("""
                SELECT gm.gm_id AS `gmId`, gm.gm_name AS `gmName`, gm.gm_code AS `gmCode`,
                       p.province_name AS `provinceName`, c.city_name AS `cityName`,
                       COUNT(DISTINCT f.af_id) AS `taskTotal`,
                       COUNT(DISTINCT CASE WHEN f.state = 3 THEN f.af_id END) AS `completedTotal`,
                       ROUND(AVG(CASE WHEN m.record_status = 'CONFIRMED' THEN m.overall_level_id END), 2) AS `averageLevel`
                FROM grid_member gm
                LEFT JOIN grid_province p ON p.province_id = gm.province_id
                LEFT JOIN grid_city c ON c.city_id = gm.city_id AND c.province_id = gm.province_id
                LEFT JOIN aqi_feedback f ON f.gm_id = CAST(gm.gm_id AS UNSIGNED)
                LEFT JOIN aqi_measurement m ON m.task_id = f.af_id
                GROUP BY gm.gm_id, gm.gm_name, gm.gm_code, p.province_name, c.city_name
                ORDER BY completedTotal DESC, taskTotal DESC, gm.gm_name
                """);
    }

    private List<Map<String, Object>> recentMeasurements() {
        return jdbcTemplate.queryForList("""
                SELECT m.measurement_id AS `measurementId`, m.task_id AS `afId`,
                       m.measured_at AS `measuredAt`, m.so2_concentration AS `so2Concentration`,
                       m.co_concentration AS `coConcentration`, m.pm25_concentration AS `pm25Concentration`,
                       m.overall_level_id AS `overallLevelId`, m.is_exceeded AS `isExceeded`,
                       p.province_name AS `provinceName`, c.city_name AS `cityName`,
                       f.address, gm.gm_name AS `gmName`
                FROM aqi_measurement m
                JOIN aqi_feedback f ON f.af_id = m.task_id
                LEFT JOIN grid_province p ON p.province_id = f.province_id
                LEFT JOIN grid_city c ON c.city_id = f.city_id AND c.province_id = f.province_id
                LEFT JOIN grid_member gm ON CAST(gm.gm_id AS UNSIGNED) = m.inspector_id
                WHERE m.record_status = 'CONFIRMED'
                ORDER BY m.measured_at DESC
                LIMIT 10
                """);
    }

    private Map<String, Object> risk() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("highPollutionTotal", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aqi_measurement WHERE record_status = 'CONFIRMED' AND overall_level_id >= 3",
                Integer.class));
        result.put("severePollutionTotal", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aqi_measurement WHERE record_status = 'CONFIRMED' AND overall_level_id >= 5",
                Integer.class));
        result.put("unassignedTotal", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aqi_feedback WHERE state = 0", Integer.class));
        result.put("alerts", jdbcTemplate.queryForList("""
                SELECT m.task_id AS `afId`, m.overall_level_id AS `overallLevelId`,
                       m.measured_at AS `measuredAt`, p.province_name AS `provinceName`,
                       c.city_name AS `cityName`, f.address
                FROM aqi_measurement m
                JOIN aqi_feedback f ON f.af_id = m.task_id
                LEFT JOIN grid_province p ON p.province_id = f.province_id
                LEFT JOIN grid_city c ON c.city_id = f.city_id AND c.province_id = f.province_id
                WHERE m.record_status = 'CONFIRMED' AND m.overall_level_id >= 3
                ORDER BY m.overall_level_id DESC, m.measured_at DESC
                LIMIT 8
                """));
        return result;
    }
}
