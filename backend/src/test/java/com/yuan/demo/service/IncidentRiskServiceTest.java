package com.yuan.demo.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentRiskServiceTest {

    private final IncidentRiskService service = new IncidentRiskService();

    @Test
    void shouldExplainHighRiskIncident() {
        Map<String, Object> result = service.assess(
                "今晚22点，某小学东侧化工园附近持续出现刺鼻异味和大量黑烟，有居民眼睛刺痛");

        assertThat((Integer) result.get("riskScore")).isGreaterThanOrEqualTo(76);
        assertThat(result.get("riskLevel")).isEqualTo("紧急");
        assertThat((Iterable<?>) result.get("triggers")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldKeepOrdinaryDustReportBelowHighRisk() {
        Map<String, Object> result = service.assess("建设路与和平街交叉口有少量道路扬尘，持续约十分钟");

        assertThat((Integer) result.get("riskScore")).isLessThan(51);
        assertThat(result.get("riskLevel")).isIn("常规", "中");
    }
}
