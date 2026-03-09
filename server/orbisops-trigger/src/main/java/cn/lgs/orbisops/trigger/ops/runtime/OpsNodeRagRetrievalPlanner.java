package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the typed retrieval plan and advisor context for node-level RAG. */
@Component
public final class OpsNodeRagRetrievalPlanner {

    public Plan plan(
            String prompt,
            String retrievalQuery,
            boolean streamingResponse,
            String projectId,
            String knowledgeBaseId,
            String knowledgeBaseScope,
            OpsNodeRagAdvisorFactory.Resources resources,
            OpsNodeRagSettings settings) {
        String safePrompt = StringUtils.hasText(prompt) ? prompt : "";
        String safeQuery = StringUtils.hasText(retrievalQuery)
                ? retrievalQuery
                : safePrompt;
        boolean streamingOptimized = streamingResponse
                && settings.ttft().optimizeStreaming();
        int vectorTopK = streamingOptimized
                ? positiveOrDefault(settings.ttft().vectorTopK(), 4)
                : 8;
        int bm25TopK = streamingOptimized
                ? positiveOrDefault(settings.ttft().bm25TopK(), 6)
                : 10;
        int finalTopK = streamingOptimized
                ? positiveOrDefault(settings.ttft().finalTopK(), 4)
                : 6;
        boolean rerankEnabled = settings.rerank().enabled()
                && resources.rerankAvailable()
                && !(streamingOptimized
                && settings.ttft().disableRerankOnStream());
        boolean llmQueryRewriteEnabled = settings.queryRewrite().llmEnabled()
                && resources.chatAvailable()
                && (!streamingOptimized
                || settings.ttft().queryRewriteEnabled());
        return new Plan(
                safePrompt,
                safeQuery,
                value(projectId),
                value(knowledgeBaseId),
                value(knowledgeBaseScope),
                resources.vectorAvailable() ? "auto" : "bm25",
                streamingOptimized,
                rerankEnabled,
                llmQueryRewriteEnabled,
                vectorTopK,
                bm25TopK,
                finalTopK);
    }

    public Map<String, Object> advisorContext(
            Plan plan,
            OpsNodeRagSettings settings) {
        OpsNodeRagSettings.QueryRewrite rewrite = settings.queryRewrite();
        OpsNodeRagSettings.Rerank rerank = settings.rerank();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("qa_retrieval_mode", plan.retrievalMode());
        context.put("qa_dynamic_search", true);
        context.put("qa_vector_top_k", plan.vectorTopK());
        context.put("qa_bm25_top_k", plan.bm25TopK());
        context.put("qa_final_top_k", plan.finalTopK());
        context.put("qa_query_rewrite_enabled",
                !plan.streamingOptimized()
                        || settings.ttft().queryRewriteEnabled());
        context.put("qa_query_rewrite_mode", rewrite.mode());
        context.put("qa_llm_query_rewrite_enabled",
                plan.llmQueryRewriteEnabled());
        context.put("qa_llm_query_rewrite_base_url", rewrite.baseUrl());
        context.put("qa_llm_query_rewrite_api_key", rewrite.apiKey());
        context.put("qa_llm_query_rewrite_path", rewrite.path());
        context.put("qa_llm_query_rewrite_model", rewrite.model());
        context.put("qa_query_rewrite_max_queries", rewrite.maxQueries());
        context.put("qa_llm_query_rewrite_timeout_seconds", rewrite.timeoutSeconds());
        context.put("qa_llm_query_rewrite_min_chars", rewrite.minChars());
        context.put("qa_llm_query_rewrite_on_low_recall", rewrite.onLowRecall());
        context.put("qa_llm_query_rewrite_low_recall_min_candidates",
                rewrite.lowRecallMinCandidates());
        context.put("qa_fail_on_degradation", settings.failOnLlmDegradation());
        context.put("qa_query_rewrite_fail_on_degradation", false);
        context.put("qa_rerank_fail_on_degradation", false);
        context.put("qa_rerank_enabled", plan.rerankEnabled());
        context.put("qa_rerank_provider", rerank.provider());
        context.put("qa_rerank_base_url", rerank.baseUrl());
        context.put("qa_rerank_path", rerank.path());
        context.put("qa_rerank_model", rerank.model());
        context.put("qa_rerank_candidate_top_k", rerank.candidateTopK());
        context.put("qa_rerank_top_n", rerank.topN());
        context.put("qa_rerank_max_doc_chars", rerank.maxDocumentChars());
        context.put("qa_exclude_memory_documents", true);
        if (StringUtils.hasText(plan.knowledgeBaseId())) {
            context.put("qa_filter_expression", knowledgeFilterExpression(
                    plan.projectId(),
                    plan.knowledgeBaseId(),
                    plan.knowledgeBaseScope()));
        }
        return context;
    }

    String knowledgeFilterExpression(
            String projectId,
            String knowledgeBaseId,
            String knowledgeBaseScope) {
        String knowledge = escapeFilterValue(knowledgeBaseId);
        String scope = StringUtils.hasText(knowledgeBaseScope)
                ? knowledgeBaseScope.trim().toUpperCase()
                : "";
        if ("PROJECT".equals(scope) && StringUtils.hasText(projectId)) {
            return "knowledge == '" + knowledge
                    + "' && knowledge_scope == 'PROJECT' && project_id == '"
                    + escapeFilterValue(projectId) + "'";
        }
        if ("GLOBAL".equals(scope)) {
            return "knowledge == '" + knowledge
                    + "' && knowledge_scope == 'GLOBAL'";
        }
        return "knowledge == '" + knowledge + "'";
    }

    private String escapeFilterValue(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    public record Plan(
            String prompt,
            String retrievalQuery,
            String projectId,
            String knowledgeBaseId,
            String knowledgeBaseScope,
            String retrievalMode,
            boolean streamingOptimized,
            boolean rerankEnabled,
            boolean llmQueryRewriteEnabled,
            int vectorTopK,
            int bm25TopK,
            int finalTopK) {
    }
}
