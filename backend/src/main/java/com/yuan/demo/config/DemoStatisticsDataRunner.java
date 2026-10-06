package com.yuan.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 为决策看板准备可重复、可比较的历史统计样本。
 * 固定主键 + INSERT IGNORE 保证重复启动不会重复累加数据。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class DemoStatisticsDataRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${neps.demo-data.enabled:true}")
    private boolean enabled;

    @Value("${neps.demo-data.records-per-year:3600}")
    private int recordsPerYear;

    private static final Region[] REGIONS = {
            new Region(1, 1, 1001, "河北省", "石家庄市", 10, 4, 3),
            new Region(1, 2, 1002, "河北省", "保定市", 2, 3, 4),
            new Region(1, 3, 1003, "河北省", "沧州市", 7, 5, 4),
            new Region(2, 35, 1004, "北京市", "北京市", 12, 3, 3),
            new Region(3, 36, 1005, "天津市", "天津市", 5, 4, 5),
            new Region(17, 3201, 1006, "江苏省", "南京市", 9, 2, 2),
            new Region(18, 3301, 1007, "浙江省", "杭州市", 10, 2, 1),
            new Region(22, 3702, 1008, "山东省", "青岛市", 4, 1, 1),
            new Region(26, 4403, 1009, "广东省", "深圳市", 13, 2, 2),
            new Region(11, 3101, 1010, "上海市", "上海市", 6, 4, 3)
    };

    // 两年的总权重均为 120，3600 条时每个权重点恰好重复 30 次。
    private static final int[] WEIGHTS_2024 = {13, 12, 14, 16, 12, 10, 8, 6, 11, 18};
    private static final int[] WEIGHTS_2025 = {14, 9, 15, 18, 13, 10, 8, 5, 12, 16};

    @Override
    public void run(String... args) {
        if (!enabled || recordsPerYear <= 0) {
            return;
        }
        seedSupervisors();
        seedYear(2024, WEIGHTS_2024);
        seedYear(2025, WEIGHTS_2025);
    }

    private void seedSupervisors() {
        String encodedPassword = passwordEncoder.encode("123456");
        List<Object[]> rows = new ArrayList<>();
        for (int regionIndex = 0; regionIndex < REGIONS.length; regionIndex++) {
            Region region = REGIONS[regionIndex];
            for (int i = 1; i <= region.supervisorCount(); i++) {
                rows.add(new Object[]{
                        supervisorTel(regionIndex, i), encodedPassword,
                        region.cityName() + "监督员" + i, region.provinceId(), region.cityId()
                });
            }
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO supervisor(tel_id, password, real_name, province_id, city_id)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE real_name=VALUES(real_name),
                    province_id=VALUES(province_id), city_id=VALUES(city_id)
                """, rows);
    }

    private void seedYear(int year, int[] weights) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aqi_feedback WHERE remarks = ? AND YEAR(af_date) = ?",
                Integer.class, "历史统计样本", year);
        if (existing != null && existing >= recordsPerYear) {
            log.info("{} 年历史统计样本已存在：{} 条", year, existing);
            return;
        }

        List<Integer> weightedRegions = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights[i]; j++) {
                weightedRegions.add(i);
            }
        }

        List<Object[]> feedbackRows = new ArrayList<>(recordsPerYear);
        List<Object[]> measurementRows = new ArrayList<>(recordsPerYear);
        int yearOffset = year - 2024;
        for (int i = 0; i < recordsPerYear; i++) {
            int regionIndex = weightedRegions.get(i % weightedRegions.size());
            Region region = REGIONS[regionIndex];
            int supervisorIndex = (i / weightedRegions.size()) % region.supervisorCount() + 1;
            String telId = supervisorTel(regionIndex, supervisorIndex);
            int level = boundedLevel((year == 2024 ? region.level2024() : region.level2025()) + levelVariation(i));
            LocalDateTime reportedAt = LocalDateTime.of(year, i % 12 + 1, i % 28 + 1,
                    8 + i % 10, i % 60);
            int feedbackId = 200_000_000 + yearOffset * 10_000 + i;
            long measurementId = 800_000_000L + yearOffset * 10_000L + i;

            feedbackRows.add(new Object[]{
                    feedbackId, telId, region.provinceId(), region.cityId(),
                    region.cityName() + "统计采样点" + (i % 60 + 1), pollutionDescription(level), level,
                    Timestamp.valueOf(reportedAt), region.gridMemberId(), Timestamp.valueOf(reportedAt.plusMinutes(10)),
                    Timestamp.valueOf(reportedAt.plusMinutes(15))
            });

            measurementRows.add(new Object[]{
                    measurementId, feedbackId, "GRID" + String.format("%03d", regionIndex + 1),
                    String.format("%06d", region.provinceId()), String.format("%06d", region.cityId()),
                    region.cityName() + "统计采样点" + (i % 60 + 1), region.gridMemberId(),
                    Timestamp.valueOf(reportedAt.plusMinutes(40)), so2(level, i), co(level, i), pm25(level, i),
                    level, level, level, Timestamp.valueOf(reportedAt.plusMinutes(45))
            });
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO aqi_feedback(
                    af_id, tel_id, province_id, city_id, address, information, estimated_grade,
                    af_date, af_time, gm_id, assign_date, assign_time, state, remarks,
                    accepted_at, accept_deadline)
                VALUES (?, ?, ?, ?, ?, ?, ?, DATE(?), TIME(?), ?, DATE(?), TIME(?), 3, '历史统计样本', ?, ?)
                ON DUPLICATE KEY UPDATE af_id=VALUES(af_id)
                """, feedbackRows.stream().map(row -> new Object[]{
                row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[7], row[8],
                row[9], row[9], row[9], row[9]
        }).toList());

        jdbcTemplate.batchUpdate("""
                INSERT INTO aqi_measurement(
                    measurement_id, task_id, grid_code, province_code, city_code, detail_address,
                    inspector_id, measured_at, so2_concentration, co_concentration, pm25_concentration,
                    so2_level_id, co_level_id, pm25_level_id, record_status, submitted_at, confirmed_at, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?, '两年历史统计样本')
                ON DUPLICATE KEY UPDATE measurement_id=VALUES(measurement_id)
                """, measurementRows.stream().map(row -> new Object[]{
                row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7],
                row[8], row[9], row[10], row[11], row[12], row[13], row[14], row[14]
        }).toList());

        log.info("已生成 {} 年历史统计样本 {} 条", year, recordsPerYear);
    }

    private String supervisorTel(int regionIndex, int memberIndex) {
        return String.format("139%02d%06d", regionIndex, memberIndex);
    }

    private int levelVariation(int i) {
        return switch (i % 10) {
            case 0 -> 1;
            case 1 -> -1;
            default -> 0;
        };
    }

    private int boundedLevel(int level) {
        return Math.max(1, Math.min(6, level));
    }

    private String pollutionDescription(int level) {
        if (level >= 5) return "工业排放与静稳天气叠加，颗粒物浓度显著升高";
        if (level >= 4) return "交通排放、道路扬尘与局地施工共同影响";
        if (level >= 3) return "早晚高峰交通排放导致轻度污染";
        return "空气扩散条件较好，污染物浓度保持较低水平";
    }

    private double so2(int level, int i) {
        double[] values = {0, 30, 80, 180, 400, 750, 1200};
        return values[level] + i % 17;
    }

    private double co(int level, int i) {
        double[] values = {0, 1.5, 4, 8, 16, 28, 42};
        return values[level] + (i % 5) * 0.1;
    }

    private double pm25(int level, int i) {
        double[] values = {0, 28, 60, 105, 170, 265, 390};
        return values[level] + i % 13;
    }

    private record Region(int provinceId, int cityId, int gridMemberId,
                          String provinceName, String cityName, int supervisorCount,
                          int level2024, int level2025) {
    }
}
