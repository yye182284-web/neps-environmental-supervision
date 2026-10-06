package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.entity.AqiFeedback;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.security.SessionAuth;
import com.yuan.demo.service.IAqiFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/** 公众反馈的提交、个人历史和管理员条件查询。 */
@RestController
@RequestMapping("/aqiFeedback")
@RequiredArgsConstructor
@Slf4j
public class AqiFeedbackController {
    private final IAqiFeedbackService aqiFeedbackService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/select")
    @RequireRole("ADMIN")
    public ResultVO<List<AqiFeedback>> select() {
        List<AqiFeedback> list = aqiFeedbackService.findAll();
        log.info("管理员查询公众反馈，共{}条", list.size());
        return new ResultVO<>(200, "查询成功", list);
    }

    @GetMapping("/query")
    @RequireRole("ADMIN")
    public ResultVO<Map<String, Object>> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) Integer provinceId,
            @RequestParam(required = false) Integer cityId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (f.address LIKE ? OR f.information LIKE ? OR f.tel_id LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (state != null) { where.append(" AND f.state = ? "); args.add(state); }
        if (provinceId != null) { where.append(" AND f.province_id = ? "); args.add(provinceId); }
        if (cityId != null) { where.append(" AND f.city_id = ? "); args.add(cityId); }
        if (startDate != null && !startDate.isBlank()) { parseDate(startDate); where.append(" AND f.af_date >= ? "); args.add(startDate); }
        if (endDate != null && !endDate.isBlank()) { parseDate(endDate); where.append(" AND f.af_date <= ? "); args.add(endDate); }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM aqi_feedback f" + where,
                Long.class, args.toArray());
        java.util.ArrayList<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(safeSize); pageArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT f.af_id AS `afId`, f.tel_id AS `telId`, f.province_id AS `provinceId`,
                       p.province_name AS `provinceName`, f.city_id AS `cityId`, c.city_name AS `cityName`,
                       f.address, f.information, f.estimated_grade AS `estimatedGrade`,
                       f.af_date AS `afDate`, f.af_time AS `afTime`, f.gm_id AS `gmId`,
                       f.assign_date AS `assignDate`, f.assign_time AS `assignTime`, f.state, f.remarks,
                       EXISTS(SELECT 1 FROM staffing_request sr
                              WHERE sr.province_id=f.province_id AND sr.city_id=f.city_id
                                AND sr.status='PENDING') AS `hasStaffingRequest`
                FROM aqi_feedback f
                JOIN grid_province p ON p.province_id = f.province_id
                JOIN grid_city c ON c.city_id = f.city_id AND c.province_id = f.province_id
                """ + where + " ORDER BY f.af_date DESC, f.af_time DESC, f.af_id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records); result.put("total", total == null ? 0 : total);
        result.put("page", safePage); result.put("size", safeSize);
        return new ResultVO<>(200, "查询成功", result);
    }

    @GetMapping("/history/{telId}")
    @RequireRole("PUBLIC")
    public ResultVO<List<AqiFeedback>> history(@PathVariable String telId, HttpServletRequest request) {
        String current = SessionAuth.subjectId(request);
        if (!telId.equals(current)) return new ResultVO<>(403, "只能查询自己的反馈记录");
        List<AqiFeedback> records = aqiFeedbackService.findAll().stream()
                .filter(item -> current.equals(item.getTelId()))
                .sorted(Comparator.comparing(AqiFeedback::getAfId).reversed())
                .toList();
        return new ResultVO<>(200, "查询成功", records);
    }

    @GetMapping("/find/{afId}")
    @RequireRole({"PUBLIC", "GRID_MEMBER", "ADMIN", "DECISION"})
    public ResultVO<AqiFeedback> findById(@PathVariable Integer afId, HttpServletRequest request) {
        AqiFeedback feedback = requiredFeedback(afId);
        verifyReadable(feedback, request);
        return new ResultVO<>(200, "查询成功", feedback);
    }

    @GetMapping("/delete/{afId}")
    @RequireRole("PUBLIC")
    @Transactional
    public ResultVO<Boolean> delete(@PathVariable Integer afId, HttpServletRequest request) {
        AqiFeedback stored = requiredFeedback(afId);
        verifyOwner(stored, request);
        if (stored.getState() != null && stored.getState() != 0) {
            return new ResultVO<>(400, "反馈已被指派，不能再删除", false);
        }
        return new ResultVO<>(200, "删除成功", aqiFeedbackService.removeById(afId));
    }

    @PostMapping("/save")
    @RequireRole("PUBLIC")
    @Transactional
    public ResultVO<Boolean> save(@RequestBody AqiFeedback feedback, HttpServletRequest request) {
        feedback.setAfId(null);
        feedback.setTelId(SessionAuth.subjectId(request));
        feedback.setGmId(null); feedback.setAssignDate(null); feedback.setAssignTime(null);
        feedback.setState(0); feedback.setRemarks(null);
        normalizeAndValidate(feedback);
        return new ResultVO<>(200, "保存成功", aqiFeedbackService.save(feedback));
    }

    @PostMapping("/update")
    @RequireRole("PUBLIC")
    @Transactional
    public ResultVO<Boolean> update(@RequestBody AqiFeedback feedback, HttpServletRequest request) {
        if (feedback == null || feedback.getAfId() == null) throw new IllegalArgumentException("反馈编号不能为空");
        AqiFeedback stored = requiredFeedback(feedback.getAfId());
        verifyOwner(stored, request);
        if (stored.getState() != null && stored.getState() != 0) {
            return new ResultVO<>(400, "反馈已被指派，不能再修改", false);
        }
        stored.setProvinceId(feedback.getProvinceId()); stored.setCityId(feedback.getCityId());
        stored.setAddress(feedback.getAddress()); stored.setInformation(feedback.getInformation());
        stored.setEstimatedGrade(feedback.getEstimatedGrade()); stored.setAfDate(feedback.getAfDate());
        stored.setAfTime(feedback.getAfTime());
        normalizeAndValidate(stored);
        return new ResultVO<>(200, "更新成功", aqiFeedbackService.updateById(stored));
    }

    private AqiFeedback requiredFeedback(Integer afId) {
        AqiFeedback feedback = aqiFeedbackService.getById(afId);
        if (feedback == null) throw new IllegalArgumentException("未找到该反馈");
        return feedback;
    }

    private void verifyOwner(AqiFeedback feedback, HttpServletRequest request) {
        if (!feedback.getTelId().equals(SessionAuth.subjectId(request))) {
            throw new IllegalArgumentException("只能维护自己提交的反馈");
        }
    }

    private void verifyReadable(AqiFeedback feedback, HttpServletRequest request) {
        if (SessionAuth.hasRole(request, "ADMIN", "DECISION")) return;
        if (SessionAuth.hasRole(request, "PUBLIC")) { verifyOwner(feedback, request); return; }
        if (SessionAuth.hasRole(request, "GRID_MEMBER") && feedback.getGmId() != null
                && feedback.getGmId().toString().equals(SessionAuth.subjectId(request))) return;
        throw new IllegalArgumentException("无权查看该反馈");
    }

    private void normalizeAndValidate(AqiFeedback feedback) {
        if (feedback.getProvinceId() == null || feedback.getCityId() == null) throw new IllegalArgumentException("省份和城市不能为空");
        Integer region = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM grid_city WHERE city_id = ? AND province_id = ?",
                Integer.class, feedback.getCityId(), feedback.getProvinceId());
        if (region == null || region == 0) throw new IllegalArgumentException("城市与省份不匹配");
        feedback.setAddress(required(feedback.getAddress(), "详细地址不能为空", 200));
        feedback.setInformation(required(feedback.getInformation(), "反馈描述不能为空", 500));
        if (feedback.getEstimatedGrade() == null || feedback.getEstimatedGrade() < 1 || feedback.getEstimatedGrade() > 6) {
            throw new IllegalArgumentException("预估AQI等级必须为1到6级");
        }
        feedback.setAfDate(parseDate(feedback.getAfDate()).toString());
        feedback.setAfTime(parseTime(feedback.getAfTime()).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private String required(String value, String message, int maxLength) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        String result = value.trim();
        if (result.length() > maxLength) throw new IllegalArgumentException(message.replace("不能为空", "不能超过" + maxLength + "个字符"));
        return result;
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (Exception e) { throw new IllegalArgumentException("反馈日期格式不正确"); }
    }

    private LocalTime parseTime(String value) {
        try { return LocalTime.parse(value.length() == 5 ? value + ":00" : value); }
        catch (DateTimeParseException | NullPointerException e) { throw new IllegalArgumentException("反馈时间格式不正确"); }
    }
}
