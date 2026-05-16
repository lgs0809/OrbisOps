package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_RAG;

/**
 * ReAct-style knowledge sub-agent. It searches the ops knowledge domain
 * and reports whether the knowledge base can actually support the question.
 */
@Service
public class RagKnowledgeOpsSubAgent extends AbstractOpsSubAgent {

    private final OpsSubAgentDecisionService decisionService;
    private final ModelAvailabilityPort aiModelAvailability;
    private final OpsRunCancellationRegistry cancellationRegistry;
    private final OpsRagKnowledgeRetrievalService retrievalService;
    private final OpsRagKnowledgeSettings settings;
    private final OpsProjectKnowledgeScopeResolver projectKnowledgeScopeResolver;
    private final OpsRagKnowledgeRuntimeResources resources;
    private final OpsRagKnowledgePolicy policy;

    @Autowired
    public RagKnowledgeOpsSubAgent(OpsSubAgentDecisionService decisionService,
                                   ModelAvailabilityPort aiModelAvailability,
                                   OpsRunCancellationRegistry cancellationRegistry,
                                   OpsRagKnowledgeRetrievalService retrievalService,
                                   OpsRagKnowledgeSettings settings,
                                   OpsProjectKnowledgeScopeResolver projectKnowledgeScopeResolver,
                                   OpsRagKnowledgeRuntimeResources resources) {
        this(decisionService,
                aiModelAvailability,
                cancellationRegistry,
                retrievalService,
                settings,
                projectKnowledgeScopeResolver,
                resources,
                new OpsRagKnowledgePolicy());
    }

    RagKnowledgeOpsSubAgent(
            OpsSubAgentDecisionService decisionService,
            ModelAvailabilityPort aiModelAvailability,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsRagKnowledgeRetrievalService retrievalService,
            OpsRagKnowledgeSettings settings,
            OpsProjectKnowledgeScopeResolver projectKnowledgeScopeResolver,
            OpsRagKnowledgeRuntimeResources resources,
            OpsRagKnowledgePolicy policy) {
        this.decisionService = decisionService;
        this.aiModelAvailability = aiModelAvailability;
        this.cancellationRegistry = cancellationRegistry;
        this.retrievalService = retrievalService;
        this.settings = settings == null ? OpsRagKnowledgeSettings.defaults() : settings;
        this.projectKnowledgeScopeResolver = projectKnowledgeScopeResolver == null
                ? OpsProjectKnowledgeScopeResolver.unavailable()
                : projectKnowledgeScopeResolver;
        this.resources = resources == null ? OpsRagKnowledgeRuntimeResources.unavailable() : resources;
        this.policy = policy == null ? new OpsRagKnowledgePolicy() : policy;
    }

    @Override
    public String source() {
        return SOURCE_RAG;
    }

    @Override
    public String agentId() {
        return "rag-knowledge-agent";
    }

    @Override
    public String displayName() {
        return "RAG 知识库子 Agent";
    }

    @Override
    public String capability() {
        return "检索 PgVector 中的 SOP、架构说明、指标字典、日志字典、历史故障案例，支持 vector/BM25/hybrid/RRF/rerank。";
    }

    @Override
    public boolean realtime() {
        return false;
    }

    @Override
    public OpsAnalysisResponseDTO.InvestigationResultDTO investigate(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                             OpsAgentRunRequestDTO request,
                                                                             OpsAnalysisResponseDTO response,
                                                                             OpsQuestionContext questionContext) {
        int maxIterations = subAgentMaxIterations(request);
        OpsAnalysisResponseDTO.InvestigationResultDTO latestResult = null;
        List<String> evidence = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = new ArrayList<>();
        String previousObservation = "";

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            assertNotCanceled(request);
            latestResult = investigateOnce(task, request, questionContext, previousObservation, iteration, maxIterations);
            assertNotCanceled(request);
            addAll(evidence, latestResult.getEvidence());
            addAll(attempts, latestResult.getAttempts());
            addAll(gaps, latestResult.getGaps());
            addAll(adjustments, latestResult.getSuggestedAdjustments());

            if (!shouldRetryWithinSubAgent(latestResult)) {
                return withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, false, maxIterations);
            }
            if (iteration == maxIterations) {
                return withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, true, maxIterations);
            }

            previousObservation = loopObservation(latestResult, iteration, maxIterations);
            attempts.add(attempt("LOOP iteration=" + (iteration + 1) + "/" + maxIterations
                            + ", nextRetrievalMode=" + policy.retrievalModeForIteration(null, iteration + 1, questionContext),
                    0,
                    "OBSERVE 后 RAG 子 Agent 自主切换检索模式并继续查询"));
        }

        return latestResult == null
                ? result(task, STATUS_ERROR, "知识库子 Agent 未执行任何查询。", evidence, attempts, gaps, adjustments, false, 0D)
                : withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, true, maxIterations);
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO investigateOnce(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                                  OpsAgentRunRequestDTO request,
                                                                                  OpsQuestionContext questionContext,
                                                                                  String previousObservation,
                                                                                  int iteration,
                                                                                  int maxIterations) {
        List<String> evidence = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = new ArrayList<>();
        OpsSubAgentDecision decision = decisionService.decide(SOURCE_RAG, task, request, questionContext, """
                当前项目的 RAG 知识库用于存放系统画像、SOP、指标字典、日志字典、历史故障案例和排障经验。
                它不包含实时日志、实时指标或最新请求状态；检索结果只能作为解释和排障建议。
                """, previousObservation);
        boolean embeddingAvailable = aiModelAvailability.isEmbeddingAvailable();
        boolean bm25Available = retrievalService.bm25Available();
        boolean rerankAvailable = aiModelAvailability.isRerankAvailable(settings.rerankApiKey());
        String retrievalMode = policy.retrievalModeForIteration(decision.retrievalMode(), iteration, questionContext);
        if ((!embeddingAvailable || !resources.vectorStoreAvailable()) && bm25Available) {
            retrievalMode = "bm25";
        }
        attempts.add(attempt("THINK iteration=" + iteration + "/" + maxIterations
                        + ", queryFocus=" + value(decision.queryFocus())
                        + ", retrievalMode=" + value(retrievalMode)
                        + ", embeddingAvailable=" + embeddingAvailable
                        + ", rerankAvailable=" + rerankAvailable
                        + ", reason=" + decision.reason(),
                0,
                decision.llmGenerated() ? "LLM rag-knowledge-agent 生成检索策略" : "规则 rag-knowledge-agent 检索策略"));

        if (!embeddingAvailable && !bm25Available) {
            gaps.add("未配置可用的 ORBISOPS_EMBEDDING_BASE_URL / ORBISOPS_EMBEDDING_API_KEY，向量 embedding 不可用；PgVector JDBC 也不可用，无法降级 BM25。");
            adjustments.add("配置 ORBISOPS_EMBEDDING_BASE_URL / ORBISOPS_EMBEDDING_API_KEY 后可启用 vector/hybrid；如需 rerank，请在模型配置中提供对应 Provider、endpoint、model 和凭据；或检查 PgVector 数据源以使用 BM25。");
            return reviewedResult(task, request, questionContext, evidence, attempts, gaps, adjustments, STATUS_BLOCKED, "知识库子 Agent 被阻塞：模型密钥和 PgVector BM25 均不可用。", false, 0D, iteration, maxIterations);
        }

        if (embeddingAvailable && !resources.vectorStoreAvailable() && !bm25Available) {
            gaps.add("VectorStore Bean 不存在，知识库不可用。");
            adjustments.add("检查 PgVector 和 embedding 配置，或先使用 ES/Prometheus 证据完成分析。");
            return reviewedResult(task, request, questionContext, evidence, attempts, gaps, adjustments, STATUS_BLOCKED, "知识库子 Agent 被阻塞：VectorStore 未初始化。", false, 0D, iteration, maxIterations);
        }

        if (!embeddingAvailable) {
            gaps.add(rerankAvailable
                    ? "未配置真实 OPENAI_EMBEDDING_API_KEY/OPENAI_API_KEY，本轮跳过 vector/hybrid，降级使用 PgVector content/metadata 的 BM25 检索，并继续使用 reranker 精排。"
                    : "未配置真实 OPENAI_EMBEDDING_API_KEY/OPENAI_API_KEY，本轮跳过 vector/hybrid，降级使用 PgVector content/metadata 的 BM25 检索；未配置 OPS_RERANK_API_KEY，跳过 rerank。");
        } else if (!resources.vectorStoreAvailable() && bm25Available) {
            gaps.add("VectorStore Bean 不存在，本轮降级使用 PgVector content/metadata 的 BM25 检索。");
        }

        String query = policy.buildQuery(request, decision);
        String knowledgeBaseId = projectKnowledgeScopeResolver.resolveKnowledgeBaseId(request);
        if (!org.springframework.util.StringUtils.hasText(knowledgeBaseId)) {
            gaps.add("当前项目未绑定知识库，不能执行项目隔离的 RAG 检索。");
            adjustments.add("请在业务系统空间中为项目绑定知识库后重试。");
            return reviewedResult(task, request, questionContext, evidence, attempts, gaps, adjustments,
                    STATUS_BLOCKED, "知识库子 Agent 被阻塞：项目未绑定知识库。", false, 0D, iteration, maxIterations);
        }
        String knowledgeFilter = projectKnowledgeScopeResolver.filterFor(knowledgeBaseId);
        try {
            OpsRagKnowledgeRetrievalService.Result retrievalResult = retrievalService.retrieve(
                    new OpsRagKnowledgeRetrievalService.Input(
                            query,
                            retrievalMode,
                            knowledgeFilter,
                            rerankAvailable,
                            resources.vectorStore(),
                            resources.multimodalEmbeddingService(),
                            resources.embeddingModel(),
                            settings.retrievalSettings()),
                    () -> assertNotCanceled(request));
            List<Document> documents = retrievalResult.documents();
            attempts.add(attempt("rag mode=" + retrievalResult.retrievalMode() + ", rewrite=" + retrievalResult.rewriteQueries() + ", vector+bm25+multimodal+rrf+rerank, knowledgeBaseId=" + knowledgeBaseId, documents == null ? 0 : documents.size(), "复用统一 RAG 检索链路"));
            if (org.springframework.util.StringUtils.hasText(retrievalResult.queryRewriteError())) {
                gaps.add("RAG LLM query rewrite 不可用，已使用规则 rewrite 继续检索：" + retrievalResult.queryRewriteError());
            }
            if (org.springframework.util.StringUtils.hasText(retrievalResult.rerankError())) {
                gaps.add("RAG rerank 不可用，已使用融合召回排序继续检索：" + retrievalResult.rerankError());
            }

            if (documents == null || documents.isEmpty()) {
                gaps.add("未检索到匹配的 SOP、指标说明或历史案例。");
                adjustments.add("补充知识库 " + knowledgeBaseId + " 的系统画像、指标字典、日志字典、Runbook 和历史故障案例。");
                adjustments.add("下一轮可切换为 " + policy.retrievalModeForIteration(retrievalMode, iteration + 1, questionContext) + " 检索模式扩大召回。");
                return reviewedResult(task, request, questionContext, evidence, attempts, gaps, adjustments, STATUS_NOT_FOUND, "知识库没有查到可用运维知识。", iteration < maxIterations, 0.2D, iteration, maxIterations);
            }

            for (Document document : documents.stream().limit(5).toList()) {
                String source = String.valueOf(document.getMetadata().getOrDefault("source", document.getMetadata().getOrDefault("knowledge", "unknown")));
                evidence.add("[" + source + "] " + abbreviate(document.getText().replace("\n", " "), 500));
            }
            return reviewedResult(task, request, questionContext, evidence, attempts, gaps, adjustments, STATUS_FOUND, "知识库已检索到可辅助判断的 SOP/背景知识。", false, 0.7D, iteration, maxIterations);
        } catch (Exception e) {
            gaps.add("知识库查询异常：" + e.getMessage());
            adjustments.add("如果实时证据充分，可以跳过知识库；否则检查 PgVector 表和 embedding 服务。");
            return reviewedResult(task, request, questionContext, evidence, attempts, gaps, adjustments, STATUS_ERROR, "知识库子 Agent 查询异常。", false, 0D, iteration, maxIterations);
        }
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO reviewedResult(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                                 OpsAgentRunRequestDTO request,
                                                                                 OpsQuestionContext questionContext,
                                                                                 List<String> evidence,
                                                                                 List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts,
                                                                                 List<String> gaps,
                                                                                 List<String> adjustments,
                                                                                 String status,
                                                                                 String summary,
                                                                                 Boolean shouldRetry,
                                                                                 Double confidence,
                                                                                 int iteration,
                                                                                 int maxIterations) {
        OpsAgentReview fallback = new OpsAgentReview(false, status, summary, gaps, adjustments, Boolean.TRUE.equals(shouldRetry) && iteration < maxIterations, confidence);
        OpsAgentReview review = decisionService.review(SOURCE_RAG, task, request, questionContext, observation(status, summary, evidence, gaps, adjustments, iteration, maxIterations), fallback);
        if (review.llmGenerated()) {
            attempts.add(attempt("REFLECT status=" + review.status() + ", confidence=" + review.confidence(), evidence.size(), "LLM rag-knowledge-agent 复盘知识库 observation"));
        }
        return result(task, review.status(), review.summary(), evidence, attempts, review.gaps(), review.suggestedAdjustments(), review.shouldRetry(), review.confidence());
    }

    private String observation(String status, String summary, List<String> evidence, List<String> gaps, List<String> adjustments, int iteration, int maxIterations) {
        return "iteration=" + iteration + "/" + maxIterations
                + "\nremainingIterations=" + Math.max(0, maxIterations - iteration)
                + "\nstatus=" + status + "\nsummary=" + summary + "\nevidence=" + String.join(" | ", evidence)
                + "\ngaps=" + String.join(" | ", gaps)
                + "\nadjustments=" + String.join(" | ", adjustments);
    }

    private void assertNotCanceled(OpsAgentRunRequestDTO request) {
        cancellationRegistry.assertNotCanceled(request == null ? null : request.getRunId());
    }

}
