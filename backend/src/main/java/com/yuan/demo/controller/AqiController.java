package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.entity.Aqi;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.service.IAqiService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author laohan
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/aqi")
@AllArgsConstructor
@Slf4j
public class AqiController {

    private static final Pattern COLOR_PATTERN = Pattern.compile("^(#[0-9A-Fa-f]{6}|[\\p{L}A-Za-z]{1,16})$");

    private final IAqiService aqiService;

    @GetMapping("/list")
    @CrossOrigin
    public ResultVO list() {
        List<Aqi> list = aqiService.list();
        log.info("查询所有空气质量指数级别信息：{}", list);
        return new ResultVO(200, "查询成功", list);
    }

    @GetMapping("/find/{aqiId}")
    @CrossOrigin
    public ResultVO findById(@PathVariable Integer aqiId) {
        Aqi aqi = aqiService.getById(aqiId);
        return new ResultVO(200, "查询成功", aqi);
    }

    @PostMapping("/save")
    @RequireRole("ADMIN")
    @ResponseBody
    @CrossOrigin
    public ResultVO save(@RequestBody Aqi aqi) {
        if (aqi == null) {
            throw new IllegalArgumentException("AQI级别数据不能为空");
        }
        if (aqi.getAqiId() == null) {
            for (int candidate = 1; candidate <= 6; candidate++) {
                if (aqiService.getById(candidate) == null) {
                    aqi.setAqiId(candidate);
                    break;
                }
            }
            if (aqi.getAqiId() == null) {
                return new ResultVO(400, "AQI六个级别均已配置，不能继续新增", false);
            }
        }
        validate(aqi, false);
        if (aqiService.getById(aqi.getAqiId()) != null) {
            return new ResultVO(400, "该AQI级别已存在", false);
        }
        boolean success = aqiService.save(aqi);
        return new ResultVO(200, "保存成功", success);
    }

    @PostMapping("/update")
    @RequireRole("ADMIN")
    @ResponseBody
    @CrossOrigin
    public ResultVO update(@RequestBody Aqi aqi) {
        validate(aqi, true);
        if (aqiService.getById(aqi.getAqiId()) == null) {
            return new ResultVO(404, "未找到该AQI级别", false);
        }
        boolean success = aqiService.updateById(aqi);
        return new ResultVO(200, "更新成功", success);
    }

    @GetMapping("/delete/{aqiId}")
    @RequireRole("ADMIN")
    @CrossOrigin
    public ResultVO delete(@PathVariable Integer aqiId) {
        validateId(aqiId);
        if (aqiService.getById(aqiId) == null) {
            return new ResultVO(404, "未找到该AQI级别", false);
        }
        boolean success = aqiService.removeById(aqiId);
        return new ResultVO(200, "删除成功", success);
    }

    private void validate(Aqi aqi, boolean update) {
        if (aqi == null) {
            throw new IllegalArgumentException("AQI级别数据不能为空");
        }
        if (aqi.getAqiId() == null) {
            throw new IllegalArgumentException(update ? "修改时AQI级别编号不能为空" : "AQI级别编号不能为空");
        }
        validateId(aqi.getAqiId());
        requireText(aqi.getChineseExplain(), "AQI级别名称", 10);
        requireText(aqi.getAqiExplain(), "AQI级别描述", 20);
        requireText(aqi.getColor(), "表示颜色", 16);
        if (!COLOR_PATTERN.matcher(aqi.getColor().trim()).matches()) {
            throw new IllegalArgumentException("表示颜色应为颜色名称或#RRGGBB格式");
        }
        requireText(aqi.getHealthImpact(), "健康影响", 500);
        requireText(aqi.getTakeSteps(), "建议措施", 500);
        validateRange(aqi.getSo2Min(), aqi.getSo2Max(), "二氧化硫");
        validateRange(aqi.getCoMin(), aqi.getCoMax(), "一氧化碳");
        validateRange(aqi.getSpmMin(), aqi.getSpmMax(), "PM2.5");
        if (aqi.getRemarks() != null && aqi.getRemarks().length() > 100) {
            throw new IllegalArgumentException("备注不能超过100个字符");
        }
    }

    private void validateId(Integer aqiId) {
        if (aqiId == null || aqiId < 1 || aqiId > 6) {
            throw new IllegalArgumentException("AQI级别编号必须为1到6");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + "不能超过" + maxLength + "个字符");
        }
    }

    private void validateRange(Integer min, Integer max, String pollutant) {
        if (min == null || max == null) {
            throw new IllegalArgumentException(pollutant + "浓度上下限不能为空");
        }
        if (min < 0 || max < 0 || min > max) {
            throw new IllegalArgumentException(pollutant + "浓度范围不正确");
        }
    }
}
