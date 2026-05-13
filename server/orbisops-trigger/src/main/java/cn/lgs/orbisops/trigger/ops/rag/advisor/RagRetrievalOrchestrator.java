package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fixed orchestration pipeline for rewrite, recall, fusion, rerank and diversity selection.
 */
@Slf4j
public final class RagRetrievalOrchestrator {

    private final RagRetrievalSettings settings;
    private final RagRecallCoordinator recallCoordinator;
    private final RagReciprocalRankFusion reciprocalRankFusion;
    private final RagMmrDiversitySelector mmrDiversitySelector;
    private final RagRerankProtocol rerankProtocol;
    private final RagQueryRewritePolicy queryRewritePolicy;
    private final RagLlmQueryRewriteProtocol llmQueryRewriteProtocol;

    public RagRetrievalOrchestrator(RagRetrievalSettings settings,
                                    RagRecallCoordinator recallCoordinator) {
        this(settings,
                recallCoordinator,
                new RagReciprocalRankFusion(),
                new RagMmrDiversitySelector(),
                new RagRerankProtocol(settings),
                new RagQueryRewritePolicy(),
                new RagLlmQueryRewriteProtocol());
    }

    RagRetrievalOrchestrator(RagRetrievalSettings settings,
                             RagRecallCoordinator recallCoordinator,
                             RagReciprocalRankFusion reciprocalRankFusion,
                             RagMmrDiversitySelector mmrDiversitySelector,
                             RagRerankProtocol rerankProtocol,
                             RagQueryRewritePolicy queryRewritePolicy,
                             RagLlmQueryRewriteProtocol llmQueryRewriteProtocol) {
        this.settings = settings == null ? new RagRetrievalSettings() : settings;
        this.recallCoordinator = recallCoordinator;
        this.reciprocalRankFusion = reciprocalRankFusion;
        this.mmrDiversitySelector = mmrDiversitySelector;
        this.rerankProtocol = rerankProtocol;
        this.queryRewritePolicy = queryRewritePolicy;
        this.llmQueryRewriteProtocol = llmQueryRewriteProtocol;
    }

    public List<Document> retrieve(String userText,
                                   Map<String, Object> context,
                                   RagRetrievalPlan plan) {
        Map<String, Object> workingContext = context == null ? new HashMap<>() : context;
        String filterExpression = filterExpression(workingContext);
        QueryRewritePlan rewritePlan = rewriteQueries(userText, workingContext);
        List<RagRankedDocument> rankedDocuments = recallCoordinator.recall(
                userText,
                workingContext,
                plan,
                filterExpression,
                rewritePlan.queries());
        List<Document> candidates = reciprocalRankFusion.fuse(rankedDocuments, plan);

        if (queryRewritePolicy.shouldRetryAfterLowRecall(
                userText,
                workingContext,
                settings,
                rewritePlan.llmGenerated(),
                candidates.size())) {
            QueryRewritePlan lowRecallRewritePlan = llmRewriteQueries(
                    userText,
                    workingContext,
                    rewritePlan.queries(),
                    true);
            if (!lowRecallRewritePlan.queries().isEmpty()
                    && !lowRecallRewritePlan.queries().equals(rewritePlan.queries())) {
                workingContext.put("qa_low_recall_rewrite_applied", true);
                workingContext.put("qa_rewrite_queries", lowRecallRewritePlan.queries());
                rankedDocuments = recallCoordinator.recall(
                        userText,
                        workingContext,
                        plan,
                        filterExpression,
                        lowRecallRewritePlan.queries());
                candidates = reciprocalRankFusion.fuse(rankedDocuments, plan);
            }
        }

        return mmrDiversitySelector.select(
                        userText,
                        rerankProtocol.rerank(userText, candidates, plan, workingContext),
                        plan.finalTopK(),
                        workingContext)
                .stream()
                .limit(plan.finalTopK())
                .collect(Collectors.toList());
    }

    private QueryRewritePlan rewriteQueries(String userText,
                                            Map<String, Object> context) {
        RagQueryRewriteDecision decision = queryRewritePolicy.initialDecision(
                userText,
                context,
                settings);
        if (!decision.rewriteEnabled()) {
            return new QueryRewritePlan(decision.queries(), false);
        }
        if (!decision.llmEligible()) {
            context.put("qa_rewrite_queries", decision.queries());
            return new QueryRewritePlan(decision.queries(), false);
        }
        QueryRewritePlan llmPlan = llmRewriteQueries(
                userText,
                context,
                decision.queries(),
                false);
        if (llmPlan.queries().isEmpty()) {
            context.put("qa_rewrite_queries", decision.queries());
            return new QueryRewritePlan(decision.queries(), false);
        }
        return llmPlan;
    }

    private QueryRewritePlan llmRewriteQueries(String userText,
                                               Map<String, Object> context,
                                               List<String> seedQueries,
                                               boolean lowRecallRetry) {
        RagLlmQueryRewriteResult result = llmQueryRewriteProtocol.rewrite(
                userText,
                context,
                settings,
                seedQueries,
                lowRecallRetry,
                configuredFilterExpression());
        if (result.degraded()) {
            rejectRewriteIfStrict(context, result.degradationError(), result.cause());
            if (result.cause() != null) {
                log.warn("RAG LLM query rewrite 失败，降级使用规则 rewrite：{}", result.cause().getMessage());
            }
            return new QueryRewritePlan(result.queries(), false);
        }
        context.put("qa_rewrite_queries", result.queries());
        context.put("qa_llm_query_rewrite_applied", true);
        return new QueryRewritePlan(result.queries(), result.generated());
    }

    private void rejectRewriteIfStrict(Map<String, Object> context,
                                       String message,
                                       Exception cause) {
        context.put("qa_llm_query_rewrite_error", message);
        context.put("qa_llm_query_rewrite_degraded", true);
        if (booleanFromContext(context, "qa_query_rewrite_fail_on_degradation", false)) {
            throw cause == null
                    ? new IllegalStateException(message)
                    : new IllegalStateException(message, cause);
        }
    }

    private String filterExpression(Map<String, Object> context) {
        Object override = context.get("qa_filter_expression");
        return override != null && StringUtils.hasText(override.toString())
                ? override.toString()
                : configuredFilterExpression();
    }

    private String configuredFilterExpression() {
        return StringUtils.hasText(settings.getFilterExpression())
                ? settings.getFilterExpression()
                : null;
    }

    private boolean booleanFromContext(Map<String, Object> context,
                                       String key,
                                       boolean defaultValue) {
        Object value = context.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private record QueryRewritePlan(List<String> queries,
                                    boolean llmGenerated) {
        private QueryRewritePlan {
            queries = queries == null
                    ? List.of()
                    : List.copyOf(new ArrayList<>(queries));
        }
    }
}
