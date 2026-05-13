package cn.lgs.orbisops.domain.runtime.retrieval.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Selects bounded retrieval modes and composes stable knowledge queries across retries. */
public final class KnowledgeRetrievalPolicy {

    public static final String DEFAULT_QUERY =
            "运维排障 SOP 指标 日志 监控 数据库 常见故障";

    public String retrievalModeForIteration(
            String preferredMode,
            int iteration,
            boolean hasRuntimeFilter) {
        String fallback = hasRuntimeFilter ? "hybrid" : "auto";
        LinkedHashSet<String> modes = new LinkedHashSet<>();
        if (hasText(preferredMode)) modes.add(preferredMode.trim());
        modes.add(fallback);
        modes.add("hybrid");
        if (hasRuntimeFilter) {
            modes.add("bm25");
            modes.add("vector");
        } else {
            modes.add("vector");
            modes.add("bm25");
        }
        List<String> ordered = new ArrayList<>(modes);
        int index = Math.max(0, Math.min(iteration - 1, ordered.size() - 1));
        return ordered.get(index);
    }

    public String buildQuery(
            String question,
            boolean llmGenerated,
            String queryFocus) {
        String baseQuestion = hasText(question)
                ? question.trim()
                : DEFAULT_QUERY;
        if (!llmGenerated || !hasText(queryFocus)) return baseQuestion;
        String normalizedFocus = queryFocus.trim();
        return baseQuestion.contains(normalizedFocus)
                ? baseQuestion
                : baseQuestion + "\n检索焦点：" + normalizedFocus;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
