package com.yuan.demo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentKnowledgeBaseTest {

    @Test
    void shouldRetrieveRelevantPm25Knowledge() {
        EnvironmentKnowledgeBase knowledgeBase = new EnvironmentKnowledgeBase();

        var sections = knowledgeBase.retrieve("PM2.5 较高时应该怎样防护？", 3);

        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).title()).contains("PM2.5");
        assertThat(knowledgeBase.size()).isGreaterThanOrEqualTo(8);
    }
}
