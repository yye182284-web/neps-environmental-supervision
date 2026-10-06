package com.yuan.demo.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A lightweight local retrieval layer. It keeps the demo fully local and gives the model
 * inspectable evidence instead of asking it to answer environmental questions from memory alone.
 */
@Service
public class EnvironmentKnowledgeBase {

    private static final List<String> DOMAIN_TERMS = List.of(
            "aqi", "pm2.5", "pm10", "so2", "二氧化硫", "co", "一氧化碳", "臭氧", "o3",
            "扬尘", "异味", "烟尘", "浓烟", "健康", "防护", "网格", "反馈", "检测", "污染", "应急"
    );

    private final List<KnowledgeSection> sections;

    public EnvironmentKnowledgeBase() {
        this.sections = loadSections();
    }

    public List<KnowledgeSection> retrieve(String question, int limit) {
        String normalized = normalize(question);
        Set<String> queryTerms = new LinkedHashSet<>();
        for (String term : DOMAIN_TERMS) {
            if (normalized.contains(term)) queryTerms.add(term);
        }
        for (int i = 0; i + 1 < normalized.length(); i++) {
            String gram = normalized.substring(i, i + 2);
            if (!gram.isBlank()) queryTerms.add(gram);
        }

        return sections.stream()
                .map(section -> new ScoredSection(section, score(section, normalized, queryTerms)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredSection::score).reversed())
                .limit(Math.max(1, Math.min(limit, 5)))
                .map(ScoredSection::section)
                .toList();
    }

    public int size() {
        return sections.size();
    }

    private int score(KnowledgeSection section, String question, Set<String> queryTerms) {
        String searchable = normalize(section.title() + " " + section.content());
        int score = question.contains(normalize(section.title())) ? 20 : 0;
        for (String term : queryTerms) {
            if (term.length() >= 2 && searchable.contains(term)) score += DOMAIN_TERMS.contains(term) ? 5 : 1;
        }
        return score;
    }

    private List<KnowledgeSection> loadSections() {
        ClassPathResource resource = new ClassPathResource("ai/environment-knowledge.md");
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            List<KnowledgeSection> loaded = new ArrayList<>();
            String currentTitle = "环境知识库";
            StringBuilder currentBody = new StringBuilder();
            for (String line : content.split("\\R")) {
                if (line.startsWith("## ")) {
                    addSection(loaded, currentTitle, currentBody);
                    currentTitle = line.substring(3).trim();
                    currentBody = new StringBuilder();
                } else if (!line.startsWith("# ")) {
                    currentBody.append(line).append('\n');
                }
            }
            addSection(loaded, currentTitle, currentBody);
            return List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载环境知识库", exception);
        }
    }

    private void addSection(List<KnowledgeSection> target, String title, StringBuilder body) {
        String text = body.toString().trim();
        if (!text.isEmpty()) target.add(new KnowledgeSection(title, text));
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public record KnowledgeSection(String title, String content) {
    }

    private record ScoredSection(KnowledgeSection section, int score) {
    }
}
