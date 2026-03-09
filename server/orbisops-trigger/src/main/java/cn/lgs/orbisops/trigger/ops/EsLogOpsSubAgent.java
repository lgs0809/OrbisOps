package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_ES;

/**
 * ReAct-style Elasticsearch log sub-agent. It owns query orchestration,
 * evidence extraction, response projection, and explicit gap reporting.
 */
@Slf4j
@Service
public class EsLogOpsSubAgent extends AbstractOpsSubAgent {

    private final OpsSubAgentDecisionService decisionService;
    private final OpsRunCancellationRegistry cancellationRegistry;
    private final OpsEsLogQueryProtocolService logQueryService;
    private final OpsEsLogSettings settings;
    private final OpsSubAgentRequestPolicy requestPolicy;
    private final OpsEsLogResponseProjector responseProjector;
    private OpsAuthoritativeDatasourceEvidenceProjector authoritativeEvidenceProjector =
            new OpsAuthoritativeDatasourceEvidenceProjector();

    public EsLogOpsSubAgent(OpsSubAgentDecisionService decisionService) {
        this(decisionService,
                new OpsRunCancellationRegistry(),
                new OpsEsLogQueryProtocolService(),
                OpsEsLogSettings.defaults(),
                new OpsSubAgentRequestPolicy(),
                new OpsEsLogResponseProjector());
    }

    @Autowired
    public EsLogOpsSubAgent(OpsSubAgentDecisionService decisionService,
                            OpsRunCancellationRegistry cancellationRegistry,
                            OpsEsLogSettings settings) {
        this(decisionService,
                cancellationRegistry,
                new OpsEsLogQueryProtocolService(),
                settings,
                new OpsSubAgentRequestPolicy(),
                new OpsEsLogResponseProjector());
    }

    EsLogOpsSubAgent(
            OpsSubAgentDecisionService decisionService,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsEsLogQueryProtocolService logQueryService,
            OpsEsLogSettings settings,
            OpsSubAgentRequestPolicy requestPolicy,
            OpsEsLogResponseProjector responseProjector) {
        this.decisionService = decisionService;
        this.cancellationRegistry = cancellationRegistry;
        this.logQueryService = logQueryService;
        this.settings = settings == null ? OpsEsLogSettings.defaults() : settings;
        this.requestPolicy = requestPolicy == null ? new OpsSubAgentRequestPolicy() : requestPolicy;
        this.responseProjector = responseProjector == null ? new OpsEsLogResponseProjector() : responseProjector;
    }

    @Autowired
    void setAuthoritativeEvidenceProjector(OpsAuthoritativeDatasourceEvidenceProjector projector) {
        this.authoritativeEvidenceProjector = projector == null
                ? new OpsAuthoritativeDatasourceEvidenceProjector()
                : projector;
    }

    @Override
    public String source() {
        return SOURCE_ES;
    }

    @Override
    public String agentId() {
        return "es-log-agent";
    }

    @Override
    public String displayName() {
        return "Elasticsearch 日志子 Agent";
    }

    @Override
    public String capability() {
        return "查询 Elasticsearch 应用日志、traceId、orderId、URI、level、logger、错误码和异常堆栈。";
    }

    @Override
    public OpsAnalysisResponseDTO.InvestigationResultDTO investigate(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                             OpsAgentRunRequestDTO request,
                                                                             OpsAnalysisResponseDTO response,
                                                                             OpsQuestionContext questionContext) {
        int maxIterations = subAgentMaxIterations(request);
        OpsAgentRunRequestDTO currentRequest = request;
        OpsAnalysisResponseDTO.InvestigationResultDTO latestResult = null;
        List<String> evidence = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = new ArrayList<>();
        String previousObservation = "";

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            assertNotCanceled(currentRequest);
            latestResult = investigateOnce(task, currentRequest, response, questionContext, previousObservation, iteration, maxIterations);
            assertNotCanceled(currentRequest);
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
            currentRequest = requestPolicy.expandRange(currentRequest, true);
            attempts.add(attempt("LOOP iteration=" + (iteration + 1) + "/" + maxIterations
                            + ", nextRange=now-" + currentRequest.getRangeMinutes() + "m..now, includeRecentLogs=" + currentRequest.getIncludeRecentLogs(),
                    0,
                    "OBSERVE 后 ES 子 Agent 自主扩大时间窗口并继续查询"));
        }

        return latestResult == null
                ? result(task, STATUS_ERROR, "ES 日志子 Agent 未执行任何查询。", evidence, attempts, gaps, adjustments, false, 0D)
                : withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, true, maxIterations);
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO investigateOnce(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                                  OpsAgentRunRequestDTO request,
                                                                                  OpsAnalysisResponseDTO response,
                                                                                  OpsQuestionContext questionContext,
                                                                                  String previousObservation,
                                                                                  int iteration,
                                                                                  int maxIterations) {
        OpsSubAgentDecision decision = decisionService.decide(SOURCE_ES, task, request, questionContext, """
                Elasticsearch 存放真实运行日志，适合按时间范围、level、traceId、orderId、URI、错误码、logger、message 过滤。
                不适合回答指标趋势、实例健康、SOP 背景知识。
                """, previousObservation);
        OpsAgentRunRequestDTO effectiveRequest = requestPolicy.applyLogDecision(
                request,
                decision.rangeMinutes(),
                decision.includeRecentLogs());
        fetchLogSummary(effectiveRequest, response, questionContext);
        OpsAnalysisResponseDTO.LogSummaryDTO logs = response.getLogSummary();
        boolean available = Boolean.TRUE.equals(response.getElasticsearchStatus().getAvailable());
        List<String> evidence = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = new ArrayList<>();
        attempts.add(attempt("THINK iteration=" + iteration + "/" + maxIterations
                        + ", queryFocus=" + value(decision.queryFocus())
                        + ", exactFilters=" + value(decision.requireExactFilters())
                        + ", reason=" + decision.reason(),
                0,
                decision.llmGenerated() ? "LLM es-log-agent 生成查询策略" : "规则 es-log-agent 查询策略"));
        attempts.add(attempt(
                "index=" + settings.index() + ", range=now-" + effectiveRequest.getRangeMinutes() + "m..now, filters=" + questionContext.describeFilters() + ", aggs=levels/top_loggers, samples=" + (Boolean.TRUE.equals(effectiveRequest.getIncludeRecentLogs()) ? settings.sampleSize() : 0),
                logs.getTotalLogs().intValue(),
                available ? "ES 查询成功" : response.getElasticsearchStatus().getMessage()));

        if (!available) {
            gaps.add("Elasticsearch 当前不可用，无法获取日志证据。");
            adjustments.add("检查 ES 容器、9200 端口和索引 " + settings.index() + "。");
            return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_BLOCKED, "ES 日志子 Agent 被阻塞：" + response.getElasticsearchStatus().getMessage(), false, 0D, iteration, maxIterations);
        }

        evidence.add("最近 " + effectiveRequest.getRangeMinutes() + " 分钟日志总量 " + logs.getTotalLogs() + " 条，ERROR " + logs.getErrorLogs() + " 条，WARN " + logs.getWarnLogs() + " 条。");
        if (logs.getTopLoggers() != null && !logs.getTopLoggers().isEmpty()) {
            evidence.add("高频 logger：" + logs.getTopLoggers().stream().limit(5).map(bucket -> bucket.getKey() + "=" + bucket.getCount()).collect(Collectors.joining(", ")));
        }
        if (response.getRecentLogs() != null && !response.getRecentLogs().isEmpty()) {
            evidence.add("已提取最近日志样本 " + response.getRecentLogs().size() + " 条。");
            List<String> anomalySamples = response.getRecentLogs().stream()
                    .filter(sample -> containsAny(sample.getLevel(), "ERROR", "WARN"))
                    .limit(3)
                    .map(this::formatLogSample)
                    .toList();
            if (!anomalySamples.isEmpty()) {
                evidence.add("异常日志样本：" + String.join(" | ", anomalySamples));
            }
        }

        if (logs.getTotalLogs() == null || logs.getTotalLogs() == 0) {
            gaps.add("当前时间窗口内没有命中日志。");
            adjustments.add("扩大日志时间窗口到 " + Math.min(effectiveRequest.getRangeMinutes() * 4, 240) + " 分钟。");
            if (!questionContext.esShouldPhrases().isEmpty()) {
                adjustments.add("保留 traceId/orderId/URI/错误码等精确条件，将业务关键词降级为 should 条件。");
            } else {
                adjustments.add("如果用户能提供 traceId/orderId/接口路径，应改用精确过滤。");
            }
            return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_NOT_FOUND, "ES 未查到当前窗口内的相关日志。", true, 0.2D, iteration, maxIterations);
        }
        if (logs.getErrorLogs() == 0 && logs.getWarnLogs() == 0) {
            gaps.add("日志有流量，但没有 ERROR/WARN，不能支撑异常结论。");
            adjustments.add("如果 Prometheus 指标异常，按异常接口 URI 或 traceId 二次查询。");
            return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_INSUFFICIENT, "ES 有日志但缺少异常级别证据。", true, 0.45D, iteration, maxIterations);
        }

        return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_FOUND, "ES 已找到可用日志证据。", false, logs.getErrorLogs() > 0 ? 0.85D : 0.65D, iteration, maxIterations);
    }

    public void collectLogSummary(OpsAgentRunRequestDTO request,
                                  OpsAnalysisResponseDTO response,
                                  OpsQuestionContext questionContext) {
        fetchLogSummary(request, response, questionContext);
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
        OpsAgentReview review = decisionService.review(SOURCE_ES, task, request, questionContext, observation(status, summary, evidence, gaps, adjustments, iteration, maxIterations), fallback);
        if (review.llmGenerated()) {
            attempts.add(attempt("REFLECT status=" + review.status() + ", confidence=" + review.confidence(), evidence.size(), "LLM es-log-agent 复盘真实日志 observation"));
        }
        if (STATUS_FOUND.equals(status) && !STATUS_FOUND.equals(review.status())) {
            attempts.add(attempt("REFLECT status preserved=" + STATUS_FOUND, evidence.size(), "规则保护：ES 已命中 ERROR/WARN 日志样本，保持日志证据状态为 FOUND"));
            review = fallback;
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

    private void fetchLogSummary(OpsAgentRunRequestDTO request, OpsAnalysisResponseDTO response, OpsQuestionContext questionContext) {
        try {
            assertNotCanceled(request);
            response.setRangeMinutes(request.getRangeMinutes());
            OpsEsLogQueryProtocolService.Result queryResult = logQueryService.execute(
                    new OpsEsLogQueryProtocolService.Input(
                            settings.baseUrl(),
                            settings.index(),
                            settings.timeoutSeconds(),
                            settings.sampleSize(),
                            request.getRangeMinutes(),
                            Boolean.TRUE.equals(request.getIncludeRecentLogs()),
                            questionContext.logLevels(),
                            questionContext.esMustPhrases(),
                            questionContext.esShouldPhrases()));
            assertNotCanceled(request);

            OpsEsLogResponseProjector.Projection projection = responseProjector.project(queryResult);
            response.setLogSummary(projection.summary());
            response.setRecentLogs(projection.recentLogs());
            response.setElasticsearchStatus(status("Elasticsearch", settings.endpoint(), true,
                    "已读取真实日志数据，filters=" + questionContext.describeFilters()));
            authoritativeEvidenceProjector.record(
                    request,
                    OpsAuthoritativeDatasourceEvidenceProjector.ELASTICSEARCH,
                    settings.endpoint(),
                    OpsAuthoritativeDatasourceEvidenceProjector.summary(
                            "rangeMinutes", request.getRangeMinutes(),
                            "logSummary", projection.summary(),
                            "recentLogs", projection.recentLogs()));
        } catch (Exception e) {
            log.warn("读取 ES 日志失败：{}", e.getMessage());
            response.setElasticsearchStatus(status("Elasticsearch", settings.endpoint(), false, e.getMessage()));
        }
    }

    private String formatLogSample(OpsAnalysisResponseDTO.LogSampleDTO sample) {
        if (sample == null) {
            return "";
        }
        String level = sample.getLevel() == null ? "" : sample.getLevel();
        String logger = sample.getLoggerName() == null ? "" : sample.getLoggerName();
        String message = abbreviate(sample.getMessage(), 260);
        return "[" + level + "] " + logger + " " + message;
    }

    private void assertNotCanceled(OpsAgentRunRequestDTO request) {
        cancellationRegistry.assertNotCanceled(request == null ? null : request.getRunId());
    }

}
