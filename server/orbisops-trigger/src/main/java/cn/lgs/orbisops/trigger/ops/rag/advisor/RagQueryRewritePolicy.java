package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Framework-neutral deterministic query rewrite policy.
 *
 * Owns rule expansion, initial LLM eligibility and low-recall retry eligibility.
 * HTTP, JSON and model response parsing remain outside this policy.
 */
public final class RagQueryRewritePolicy {

    public RagQueryRewriteDecision initialDecision(String userText,
                                                    Map<String, Object> context,
                                                    RagRetrievalSettings configured) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        RagRetrievalSettings settings = configured == null
                ? new RagRetrievalSettings()
                : configured;
        boolean enabled = booleanFromContext(safeContext, "qa_query_rewrite_enabled", true);
        if (!enabled || !hasText(userText)) {
            return new RagQueryRewriteDecision(List.of(userText), false, false);
        }

        List<String> queries = ruleQueries(userText, safeContext, settings);
        String mode = valueFromContext(safeContext, "qa_query_rewrite_mode", settings.getQueryRewriteMode());
        mode = hasText(mode) ? mode.toLowerCase(Locale.ROOT) : "rule";
        boolean llmEnabled = booleanFromContext(
                safeContext,
                "qa_llm_query_rewrite_enabled",
                Boolean.TRUE.equals(settings.getLlmQueryRewriteEnabled()));
        boolean llmEligible = llmEnabled
                && ("llm".equals(mode)
                || ("hybrid".equals(mode) && isComplexQuery(userText, safeContext, settings)));
        return new RagQueryRewriteDecision(queries, true, llmEligible);
    }

    public boolean shouldRetryAfterLowRecall(String userText,
                                             Map<String, Object> context,
                                             RagRetrievalSettings configured,
                                             boolean llmGenerated,
                                             int candidateCount) {
        if (llmGenerated) {
            return false;
        }
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        RagRetrievalSettings settings = configured == null
                ? new RagRetrievalSettings()
                : configured;
        boolean llmEnabled = booleanFromContext(
                safeContext,
                "qa_llm_query_rewrite_enabled",
                Boolean.TRUE.equals(settings.getLlmQueryRewriteEnabled()));
        boolean retryEnabled = booleanFromContext(
                safeContext,
                "qa_llm_query_rewrite_on_low_recall",
                Boolean.TRUE.equals(settings.getLlmQueryRewriteOnLowRecall()));
        String mode = valueFromContext(safeContext, "qa_query_rewrite_mode", settings.getQueryRewriteMode());
        int minCandidates = intFromContext(
                safeContext,
                "qa_llm_query_rewrite_low_recall_min_candidates",
                positiveOrDefault(settings.getLlmQueryRewriteLowRecallMinCandidates(), 2));
        return llmEnabled
                && retryEnabled
                && ("llm".equalsIgnoreCase(mode) || "hybrid".equalsIgnoreCase(mode))
                && hasText(userText)
                && candidateCount < Math.max(1, minCandidates);
    }

    private List<String> ruleQueries(String userText,
                                     Map<String, Object> context,
                                     RagRetrievalSettings settings) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(userText);
        String lower = userText.toLowerCase(Locale.ROOT);
        if (lower.contains("慢sql") || lower.contains("慢 sql") || lower.contains("slow sql")
                || lower.contains("索引") || lower.contains("全表")) {
            queries.add(userText + " query_time rows_examined rows_sent full table scan missing index lock wait explain");
        }
        if (lower.contains("错误") || lower.contains("异常") || lower.contains("error")
                || lower.contains("exception") || lower.contains("trace")) {
            queries.add(userText + " error exception stacktrace traceId root cause rollback timeout failed");
        }
        if (lower.contains("prometheus") || lower.contains("指标") || lower.contains("qps")
                || lower.contains("延迟") || lower.contains("报警")) {
            queries.add(userText + " prometheus metric qps latency error_rate cpu memory saturation alert");
        }
        if (lower.contains("图片") || lower.contains("截图") || lower.contains("架构图")
                || lower.contains("流程图")) {
            queries.add(userText + " image screenshot diagram architecture flow chart evidence description");
        }
        int maxQueries = intFromContext(
                context,
                "qa_query_rewrite_max_queries",
                positiveOrDefault(settings.getLlmQueryRewriteMaxQueries(), 4));
        return queries.stream().limit(clamp(maxQueries, 1, 8)).toList();
    }

    private boolean isComplexQuery(String userText,
                                   Map<String, Object> context,
                                   RagRetrievalSettings settings) {
        int minChars = intFromContext(
                context,
                "qa_llm_query_rewrite_min_chars",
                positiveOrDefault(settings.getLlmQueryRewriteMinChars(), 18));
        if (userText.length() >= minChars
                && userText.matches(".*(为什么|怎么回事|可能|分析|排查|定位|诊断|影响|根因|复盘|最近|变慢).*")) {
            return true;
        }
        String lower = userText.toLowerCase(Locale.ROOT);
        return lower.contains("this")
                || lower.contains("that")
                || lower.contains("why")
                || lower.contains("analyze")
                || lower.contains("root cause")
                || lower.contains("slow")
                || lower.contains("timeout")
                || userText.contains("这个")
                || userText.contains("刚才")
                || userText.contains("上面");
    }

    private String valueFromContext(Map<String, Object> context, String key, String defaultValue) {
        Object value = context.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private int intFromContext(Map<String, Object> context, String key, int defaultValue) {
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean booleanFromContext(Map<String, Object> context, String key, boolean defaultValue) {
        Object value = context.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean hasText(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
