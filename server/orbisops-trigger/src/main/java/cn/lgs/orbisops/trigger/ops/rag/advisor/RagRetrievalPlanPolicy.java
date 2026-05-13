package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic retrieval planning policy.
 *
 * Resolves advisor defaults and qa_* runtime overrides into one typed plan without
 * depending on Spring AI, repositories, HTTP clients, FastJSON or recall adapters.
 */
public final class RagRetrievalPlanPolicy {

    private static final int DEFAULT_MAX_CONTEXT_CHARS = 12000;
    private static final Set<String> RETRIEVAL_MODES = Set.of("vector", "bm25", "hybrid");
    private static final Set<String> RERANK_PROVIDERS = Set.of("none", "voyage", "cohere");

    public RagRetrievalPlan resolve(String userText,
                                    Map<String, Object> context,
                                    RagRetrievalSettings configured,
                                    int frameworkDefaultTopK) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        RagRetrievalSettings settings = configured == null
                ? new RagRetrievalSettings()
                : configured;

        String mode = valueFromContext(safeContext, "qa_retrieval_mode", settings.getRetrievalMode());
        boolean dynamicSearch = booleanFromContext(
                safeContext,
                "qa_dynamic_search",
                settings.isDynamicSearch());

        if (!hasText(mode)) {
            mode = "vector";
        }
        mode = mode.toLowerCase(Locale.ROOT);
        if (dynamicSearch && "auto".equals(mode)) {
            mode = inferRetrievalMode(userText);
        }

        int fallbackTopK = settings.getTopK() > 0 ? settings.getTopK() : frameworkDefaultTopK;
        int vectorTopK = intFromContext(
                safeContext,
                "qa_vector_top_k",
                positiveOrDefault(settings.getVectorTopK(), fallbackTopK));
        int bm25TopK = intFromContext(
                safeContext,
                "qa_bm25_top_k",
                positiveOrDefault(settings.getBm25TopK(), fallbackTopK));
        int finalTopK = intFromContext(
                safeContext,
                "qa_final_top_k",
                positiveOrDefault(settings.getFinalTopK(), Math.max(vectorTopK, bm25TopK)));
        int maxContextChars = intFromContext(
                safeContext,
                "qa_max_context_chars",
                positiveOrDefault(settings.getMaxContextChars(), DEFAULT_MAX_CONTEXT_CHARS));

        boolean rerankEnabled = booleanFromContext(
                safeContext,
                "qa_rerank_enabled",
                Boolean.TRUE.equals(settings.getRerankEnabled()));
        String rerankProvider = valueFromContext(
                safeContext,
                "qa_rerank_provider",
                settings.getRerankProvider());
        String rerankBaseUrl = valueFromContext(
                safeContext,
                "qa_rerank_base_url",
                settings.getRerankBaseUrl());
        String rerankPath = valueFromContext(
                safeContext,
                "qa_rerank_path",
                settings.getRerankPath());
        String rerankModel = valueFromContext(
                safeContext,
                "qa_rerank_model",
                settings.getRerankModel());
        int rerankCandidateTopK = intFromContext(
                safeContext,
                "qa_rerank_candidate_top_k",
                positiveOrDefault(settings.getRerankCandidateTopK(), Math.max(finalTopK * 3, 12)));
        int rerankTopN = intFromContext(
                safeContext,
                "qa_rerank_top_n",
                positiveOrDefault(settings.getRerankTopN(), finalTopK));
        int rerankMaxDocChars = intFromContext(
                safeContext,
                "qa_rerank_max_doc_chars",
                positiveOrDefault(settings.getRerankMaxDocChars(), 1200));

        vectorTopK = clamp(vectorTopK, 1, 20);
        bm25TopK = clamp(bm25TopK, 1, 30);
        finalTopK = clamp(finalTopK, 1, 20);
        maxContextChars = clamp(maxContextChars, 1000, 50000);

        if (!rerankEnabled) {
            rerankProvider = "none";
        } else {
            rerankProvider = hasText(rerankProvider)
                    ? rerankProvider.toLowerCase(Locale.ROOT)
                    : "none";
            if (!RERANK_PROVIDERS.contains(rerankProvider)) {
                rerankProvider = "none";
            }
        }

        rerankCandidateTopK = clamp(rerankCandidateTopK, finalTopK, 50);
        rerankTopN = clamp(rerankTopN, 1, finalTopK);
        rerankMaxDocChars = clamp(rerankMaxDocChars, 300, 6000);

        if (rerankEnabled) {
            vectorTopK = Math.max(vectorTopK, Math.min(rerankCandidateTopK, 20));
            bm25TopK = Math.max(bm25TopK, Math.min(rerankCandidateTopK, 30));
        }

        if (!RETRIEVAL_MODES.contains(mode)) {
            mode = "vector";
        }

        return new RagRetrievalPlan(
                mode,
                vectorTopK,
                bm25TopK,
                finalTopK,
                maxContextChars,
                rerankEnabled,
                rerankProvider,
                rerankBaseUrl,
                rerankPath,
                rerankModel,
                rerankCandidateTopK,
                rerankTopN,
                rerankMaxDocChars);
    }

    private String inferRetrievalMode(String userText) {
        String lower = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        if (lower.contains("图片") || lower.contains("截图") || lower.contains("图表")
                || lower.contains("架构图") || lower.contains("流程图") || lower.contains("视觉")
                || lower.contains("image") || lower.contains("screenshot") || lower.contains("diagram")) {
            return "hybrid";
        }
        if (lower.contains("grep") || lower.contains("精确") || lower.contains("包含") || lower.contains("错误码")
                || lower.contains("traceid") || lower.contains("trace_id") || lower.contains("orderid")) {
            return "bm25";
        }
        if (lower.contains("日志") || lower.contains("log") || lower.contains("error") || lower.contains("exception")
                || lower.contains("warn") || lower.contains("报警") || lower.contains("堆栈")) {
            return "hybrid";
        }
        return "vector";
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
