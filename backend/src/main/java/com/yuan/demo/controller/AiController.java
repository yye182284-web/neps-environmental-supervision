package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.service.AiAssistantService;
import com.yuan.demo.service.IncidentRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.security.SessionAuth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 环境质量智能咨询，同时保留兼容旧前端的 /ask 流式接口。 */
@RestController
@CrossOrigin(origins = "http://localhost:8080")
@Tag(name = "环境质量智能咨询", description = "通过本机 Ollama 提供流式环境质量问答")
@RequireRole
public class AiController {

    private final JdbcTemplate jdbcTemplate;
    private final AiAssistantService assistantService;
    private final IncidentRiskService incidentRiskService;

    public AiController(JdbcTemplate jdbcTemplate,
                        AiAssistantService assistantService,
                        IncidentRiskService incidentRiskService) {
        this.jdbcTemplate = jdbcTemplate;
        this.assistantService = assistantService;
        this.incidentRiskService = incidentRiskService;
    }

    @PostMapping("/ai/public/feedback-draft")
    @RequireRole("PUBLIC")
    @Operation(summary = "公众监督员现场信息辅助录入")
    public ResultVO<Map<String, Object>> feedbackDraft(@RequestBody Map<String, Object> body) {
        String text = requiredText(body.get("text"), "请先描述现场情况");
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> region = resolveRegion(text);
        if (!region.isEmpty()) result.putAll(region);
        String address = extractAddress(text);
        result.put("address", address);
        result.put("information", normalizeDescription(text, address));
        result.put("estimatedGrade", estimateGrade(text));
        result.put("afDate", LocalDate.now().toString());
        result.put("afTime", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        result.put("explanation", addressQualityHint(address));
        return new ResultVO<>(200, "现场信息解析完成", result);
    }

    @PostMapping("/ai/grid/inspection-plan")
    @RequireRole("GRID_MEMBER")
    @Operation(summary = "网格员智能生成现场检测方案")
    public ResultVO<Map<String, Object>> inspectionPlan(@RequestBody Map<String, Object> body) {
        String text = String.valueOf(body.getOrDefault("information", "现场空气质量异常"));
        int grade = number(body.get("estimatedGrade"), 1);
        String focus = text.contains("异味") ? "重点排查挥发性气味来源与周边排放口"
                : text.contains("烟") || text.contains("扬尘") ? "重点核验颗粒物来源、风向和施工抑尘措施"
                : "按标准点位完成 SO₂、CO、PM2.5 三项采样";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskLevel", grade >= 5 ? "高" : grade >= 3 ? "中" : "常规");
        result.put("checklist", List.of("确认检测仪器校准状态", focus, "记录检测时间、详细地址和现场照片", "复核异常读数后提交最终结果"));
        result.put("suggestedRemark", "现场检测建议：" + focus + "；完成仪器校准与异常读数复核。"
                + (grade >= 3 ? "建议佩戴防护装备并缩短连续暴露时间。" : "按常规防护流程开展检测。"));
        return new ResultVO<>(200, "检测方案已生成", result);
    }

    @PostMapping("/ai/admin/dispatch-advice")
    @RequireRole("ADMIN")
    @Operation(summary = "系统管理员智能调度建议")
    public ResultVO<Map<String, Object>> dispatchAdvice(@RequestBody Map<String, Object> body) {
        int afId = number(body.get("afId"), 0);
        if (afId <= 0) throw new IllegalArgumentException("反馈编号不能为空");
        Map<String, Object> feedback = jdbcTemplate.queryForMap(
                "SELECT province_id, city_id FROM aqi_feedback WHERE af_id = ?", afId);
        List<Map<String, Object>> members = jdbcTemplate.queryForList("""
                SELECT gm.gm_id AS gmId, gm.gm_name AS gmName, gm.gm_code AS gmCode,
                       gm.province_id AS provinceId, gm.city_id AS cityId,
                       SUM(CASE WHEN f.state IN (1,2) THEN 1 ELSE 0 END) AS currentTaskCount
                FROM grid_member gm LEFT JOIN aqi_feedback f ON f.gm_id = gm.gm_id
                WHERE gm.state = 0 AND gm.province_id=? AND gm.city_id=?
                GROUP BY gm.gm_id, gm.gm_name, gm.gm_code, gm.province_id, gm.city_id
                ORDER BY currentTaskCount, gm.gm_id LIMIT 1
                """, feedback.get("province_id"), feedback.get("city_id"));
        if (members.isEmpty()) {
            return new ResultVO<>(409, "本地没有可用网格员，请向决策者申请增配人员",
                    Map.of("staffingRequired", true, "reason", "禁止跨地区调度，当前反馈所在城市没有可用网格员"));
        }
        Map<String, Object> member = members.get(0);
        Map<String, Object> result = new LinkedHashMap<>(member);
        result.put("matchType", "本地网格匹配");
        result.put("reason", "仅在反馈所在城市内按在办任务量择优，建议由" + member.get("gmName")
                + "（" + member.get("gmCode") + "）执行。当前在办任务 " + member.get("currentTaskCount") + " 项。");
        return new ResultVO<>(200, "调度建议已生成", result);
    }

    @GetMapping("/ai/decision/summary")
    @RequireRole("DECISION")
    @Operation(summary = "决策者智能生成监测研判")
    public ResultVO<Map<String, Object>> decisionSummary(HttpServletRequest ignored) {
        Map<String, Object> stats = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       SUM(overall_level_id >= 3) AS pollution,
                       SUM(overall_level_id >= 5) AS severe,
                       ROUND(AVG(overall_level_id), 2) AS averageLevel,
                       COUNT(DISTINCT province_code) AS provinceCount
                FROM aqi_measurement WHERE record_status='CONFIRMED'
                """);
        long total = longValue(stats.get("total"));
        long pollution = longValue(stats.get("pollution"));
        long severe = longValue(stats.get("severe"));
        Map<String, Object> workforce = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) total,
                       SUM(gm.state=0 AND EXISTS(SELECT 1 FROM aqi_feedback f WHERE f.gm_id=CAST(gm.gm_id AS UNSIGNED) AND f.state IN(1,2))) busy,
                       SUM(gm.state=0 AND NOT EXISTS(SELECT 1 FROM aqi_feedback f WHERE f.gm_id=CAST(gm.gm_id AS UNSIGNED) AND f.state IN(1,2))) idle,
                       SUM(gm.state<>0) unavailable
                FROM grid_member gm
                """);
        Long pendingRequests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staffing_request WHERE status='PENDING'", Long.class);
        List<Map<String, Object>> cityRisks = jdbcTemplate.queryForList("""
                SELECT c.city_name AS cityName,ROUND(AVG(m.overall_level_id),2) averageLevel,
                       ROUND(100*SUM(m.overall_level_id>=3)/COUNT(*),2) pollutionRate
                FROM aqi_measurement m JOIN grid_city c ON c.city_id=CAST(m.city_code AS UNSIGNED)
                WHERE m.record_status='CONFIRMED' AND YEAR(m.measured_at)=YEAR(CURDATE())-1
                GROUP BY c.city_name ORDER BY averageLevel DESC
                """);
        Map<String, Object> mostPolluted = cityRisks.isEmpty() ? Map.of() : cityRisks.get(0);
        Map<String, Object> bestGoverned = cityRisks.isEmpty() ? Map.of() : cityRisks.get(cityRisks.size() - 1);
        String conclusion = total == 0 ? "当前尚无有效实测数据，建议优先补充重点区域监测。"
                : severe > 0 ? "监测结果存在重度及以上污染记录，应启动重点区域溯源和复测。"
                : pollution > 0 ? "总体空气质量可控，但存在轻度及以上污染点位，需要持续跟踪。"
                : "当前实测结果总体优良，建议维持常态化网格巡查。";
        Map<String, Object> result = new LinkedHashMap<>(stats);
        result.put("workforce", workforce);
        result.put("pendingStaffingRequests", pendingRequests == null ? 0 : pendingRequests);
        result.put("mostPolluted", mostPolluted);
        result.put("bestGoverned", bestGoverned);
        result.put("conclusion", conclusion + (mostPolluted.isEmpty() ? ""
                : " 上一年度污染风险最高区域为" + mostPolluted.get("cityName")
                + "，治理表现较好区域为" + bestGoverned.get("cityName") + "。"));
        List<String> actions = new java.util.ArrayList<>();
        if (pendingRequests != null && pendingRequests > 0) actions.add("优先审议 " + pendingRequests + " 项本地网格员增配申请，禁止用外地人员替代本地长期缺口");
        if (!mostPolluted.isEmpty()) actions.add("对" + mostPolluted.get("cityName") + "开展工业排放、交通源和施工扬尘联合溯源，并增加重点时段复测");
        actions.add("按年度环比跟踪污染等级和报告量，区分环境改善与监督覆盖不足");
        actions.add("在污染率不低但报告较少的地区增配质量监督员并检查反馈渠道");
        actions.add("对治理较好地区保持常态化监测，复制有效治理措施并防止反弹");
        result.put("actions", actions);
        return new ResultVO<>(200, "智能研判已生成", result);
    }

    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "兼容旧版的流式环境质量问答")
    public Flux<String> ask(@RequestParam("ask") String ask, HttpServletRequest request) {
        return assistantService.chat(new AiAssistantService.ChatRequest("legacy-chat", ask, "explain"),
                request, SessionAuth.role(request));
    }

    @PostMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "带本地知识检索、业务数据上下文和多轮记忆的流式问答")
    public Flux<String> chat(@RequestBody AiAssistantService.ChatRequest body, HttpServletRequest request) {
        return assistantService.chat(body, request, SessionAuth.role(request));
    }

    @GetMapping("/ai/capabilities")
    @Operation(summary = "获取当前角色可用的 AI 模式和建议问题")
    public ResultVO<Map<String, Object>> capabilities(HttpServletRequest request) {
        return new ResultVO<>(200, "AI 能力加载完成", assistantService.capabilities(SessionAuth.role(request)));
    }

    @DeleteMapping("/ai/conversations/{conversationId}")
    @Operation(summary = "清除当前会话的短期对话记忆")
    public ResultVO<Boolean> clearConversation(@PathVariable String conversationId, HttpServletRequest request) {
        assistantService.clear(conversationId, request);
        return new ResultVO<>(200, "对话记忆已清除", true);
    }

    @PostMapping("/ai/risk-assessment")
    @Operation(summary = "可解释的现场污染风险预研判")
    public ResultVO<Map<String, Object>> riskAssessment(@RequestBody Map<String, Object> body) {
        return new ResultVO<>(200, "风险预研判完成",
                incidentRiskService.assess(requiredText(body.get("text"), "请先输入现场情况")));
    }

    private Map<String, Object> resolveRegion(String text) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.province_id AS provinceId, p.province_name AS provinceName,
                       c.city_id AS cityId, c.city_name AS cityName
                FROM grid_city c JOIN grid_province p ON p.province_id=c.province_id
                ORDER BY LENGTH(c.city_name) DESC
                """);
        for (Map<String, Object> row : rows) {
            String city = String.valueOf(row.get("cityName"));
            String cityShort = city.replace("市", "").replace("地区", "");
            if (text.contains(city) || text.contains(cityShort)) return row;
        }
        for (Map<String, Object> row : rows) {
            String province = String.valueOf(row.get("provinceName"));
            String shortName = province.replace("省", "").replace("市", "")
                    .replace("自治区", "").replace("壮族", "").replace("回族", "").replace("维吾尔", "");
            if (text.contains(province) || text.contains(shortName)) return row;
        }
        return Map.of();
    }

    private String extractAddress(String text) {
        String cleaned = text.replaceAll("\\s+", "")
                .replaceFirst("^(我在|我位于|位于|地点是|地址是|现场位于|现场在)", "")
                .trim();
        String firstClause = cleaned.split("[，,。；;！!]", 2)[0];
        // 现场现象不能混入详细地址：截断到最早的污染或观察描述词之前。
        String[] markers = {"附近出现", "周边出现", "现场出现", "发现", "存在", "发生",
                "有明显", "有大量", "有少量", "有轻微", "可见", "闻到", "出现",
                "扬尘", "烟尘", "浓烟", "黑烟", "异味", "雾霾", "灰霾", "污染"};
        int cut = firstClause.length();
        for (String marker : markers) {
            int index = firstClause.indexOf(marker);
            if (index > 0 && index < cut) cut = index;
        }
        String address = firstClause.substring(0, cut)
                .replaceFirst("(附近|周边|路段|现场)$", "")
                .replaceFirst("^(在|于)", "")
                .trim();
        if (address.isBlank()) address = firstClause.trim();
        return address.length() <= 100 ? address : address.substring(0, 100);
    }

    private String normalizeDescription(String text, String address) {
        String source = text.replaceAll("\\s+", " ").trim();
        String phenomenon = source;
        if (!address.isBlank()) {
            int addressEnd = source.replace(" ", "").indexOf(address.replace(" ", ""));
            if (addressEnd >= 0) {
                // 地址后的文本通常才是现场现象，删除“附近/出现”等连接词后形成规范描述。
                int rawEnd = Math.min(source.length(), addressEnd + address.length());
                phenomenon = source.substring(rawEnd)
                        .replaceFirst("^[附近周边路段现场，,。；;：:]*(出现|发现|存在|发生|有)?", "")
                        .trim();
            }
        }
        if (phenomenon.isBlank()) phenomenon = "现场空气质量存在异常，需要进一步核查";
        String type = source.matches(".*(扬尘|烟尘|灰尘|颗粒物).*?") ? "颗粒物污染"
                : source.matches(".*(异味|刺鼻|臭味).*?") ? "异味污染"
                : source.matches(".*(浓烟|黑烟|冒烟).*?") ? "烟气污染" : "空气质量异常";
        String level = estimateGrade(source) >= 5 ? "现象严重" : estimateGrade(source) >= 3 ? "现象明显" : "现象轻微";
        String result = "公众监督员反映：" + phenomenon.replaceAll("[。；;]+$", "")
                + "。初步识别为" + type + "，" + level + "，建议属地网格员现场检测核实。";
        return result.length() <= 500 ? result : result.substring(0, 500);
    }

    private String addressQualityHint(String address) {
        boolean detailed = address.matches(".*(路|街|道|巷|村|镇|乡|区|县|号|园|厂|小区|大厦|广场).*?");
        return detailed
                ? "已提取行政区划和道路地点，并整理反馈描述；请核对门牌号或地标后提交。"
                : "已完成现场信息解析，但地点精度不足，请补充道路、门牌号或明确地标后提交。";
    }

    private int estimateGrade(String text) {
        if (text.matches(".*(严重|刺鼻|看不清|大量黑烟|呼吸困难).*")) return 5;
        if (text.matches(".*(浓烟|明显异味|重度|灰霾).*")) return 4;
        if (text.matches(".*(扬尘|烟尘|轻度|有异味|雾霾).*")) return 3;
        if (text.matches(".*(轻微|少量|一般).*")) return 2;
        return 1;
    }

    private String requiredText(Object value, String message) {
        if (value == null || String.valueOf(value).trim().isEmpty()) throw new IllegalArgumentException(message);
        return String.valueOf(value).trim();
    }

    private int number(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
