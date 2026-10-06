package com.yuan.demo;

import com.yuan.demo.entity.AqiFeedback;
import com.yuan.demo.controller.StatisticsController;
import com.yuan.demo.security.SessionAuth;
import com.yuan.demo.service.IAqiFeedbackService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoApplicationTests {

    @Test
    void contextLoads() {
    }

    @Resource
    private IAqiFeedbackService aqiFeedbackService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private StatisticsController statisticsController;

    @Test
    void test1(){
        assertNotNull(aqiFeedbackService);
    }

    @Test
    void testselect(){
        System.out.println("测试");
        List<AqiFeedback> list = aqiFeedbackService.list();
        assertNotNull(list);
    }
    @Test
    void testadd(){
        System.out.println("测试增加");
        AqiFeedback feedback = createFeedback("测试增加");
        assertTrue(aqiFeedbackService.save(feedback));
        assertNotNull(feedback.getAfId());
    }

    @Test
    void testfindById(){
        System.out.println("测试查询");
        AqiFeedback created = createFeedback("测试查询");
        assertTrue(aqiFeedbackService.save(created));
        AqiFeedback feedback = aqiFeedbackService.getById(created.getAfId());
        assertNotNull(feedback);
        assertEquals("测试查询", feedback.getAddress());
    }

    @Test
    void testdelete(){
        System.out.println("测试删除");
        AqiFeedback created = createFeedback("测试删除");
        assertTrue(aqiFeedbackService.save(created));
        assertTrue(aqiFeedbackService.removeById(created.getAfId()));
        assertNull(aqiFeedbackService.getById(created.getAfId()));
    }

    @Test
    void testUpdate(){
        System.out.println("测试修改");
        AqiFeedback feedback = createFeedback("修改前");
        assertTrue(aqiFeedbackService.save(feedback));
        feedback.setAddress("河北理工真好");
        assertTrue(aqiFeedbackService.updateById(feedback));
        assertEquals("河北理工真好", aqiFeedbackService.getById(feedback.getAfId()).getAddress());
    }

    @Test
    void protectedApiRequiresLoginAndCorrectRole() throws Exception {
        mockMvc.perform(get("/task/available-members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/task/available-members")
                        .sessionAttr(SessionAuth.ROLE, "PUBLIC")
                        .sessionAttr(SessionAuth.SUBJECT_ID, "13800000000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/task/available-members")
                        .sessionAttr(SessionAuth.ROLE, "ADMIN")
                        .sessionAttr(SessionAuth.SUBJECT_ID, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void storedPasswordsAreIrreversiblyEncoded() {
        for (String table : List.of("supervisor", "grid_member", "system_account")) {
            Integer plainTextCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table
                            + " WHERE password IS NOT NULL AND password NOT LIKE '$2%'", Integer.class);
            assertEquals(0, plainTextCount, table + " 中不应存在明文密码");
        }
    }

    @Test
    void finalAqiGradeUsesHighestPollutantGrade() {
        String expression = jdbcTemplate.queryForObject("""
                SELECT generation_expression
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'aqi_measurement'
                  AND column_name = 'overall_level_id'
                """, String.class);
        assertNotNull(expression);
        assertTrue(expression.toLowerCase().contains("greatest"));
        Integer inconsistent = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM aqi_measurement
                WHERE overall_level_id <> GREATEST(so2_level_id, co_level_id, pm25_level_id)
                """, Integer.class);
        assertEquals(0, inconsistent);
    }

    @Test
    void decisionStatisticsAreCompleteAndFastEnough() {
        Map<String, Object> data = assertTimeout(Duration.ofSeconds(5),
                () -> statisticsController.dashboard(false).getData());
        assertNotNull(data.get("overview"));
        assertNotNull(data.get("measurement"));
        assertNotNull(data.get("distribution"));
        assertNotNull(data.get("province"));
        assertNotNull(data.get("cityRanking"));
        assertNotNull(data.get("gridPerformance"));
        List<?> trend = (List<?>) data.get("trend");
        assertEquals(12, trend.size(), "最近12个月应包含无数据月份");
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private AqiFeedback createFeedback(String address) {
        AqiFeedback feedback = new AqiFeedback();
        feedback.setTelId("13800000000");
        feedback.setProvinceId(1);
        feedback.setCityId(1);
        feedback.setAddress(address);
        feedback.setInformation("单元测试临时反馈");
        feedback.setEstimatedGrade(1);
        feedback.setAfDate("2026-09-10");
        feedback.setAfTime("10:00:00");
        feedback.setState(0);
        return feedback;
    }
}
