package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_PROM;

/**
 * ReAct-style Prometheus metric sub-agent. It owns query orchestration,
 * evidence extraction, response projection, and explicit gap reporting.
 */
@Slf4j
@Service
public class PrometheusMetricOpsSubAgent extends AbstractOpsSubAgent {

    private final OpsSubAgentDecisionService decisionService;
    private final OpsRunCancellationRegistry cancellationRegistry;
    private final OpsPrometheusQueryProtocolService metricQueryService;
    private final OpsPrometheusSettings settings;
    private final OpsSubAgentRequestPolicy requestPolicy;
    private final OpsPrometheusResponseProjector responseProjector;
    private OpsAuthoritativeDatasourceEvidenceProjector authoritativeEvidenceProjector =
            new OpsAuthoritativeDatasourceEvidenceProjector();

    public PrometheusMetricOpsSubAgent(OpsSubAgentDecisionService decisionService) {
        this(decisionService,
                new OpsRunCancellationRegistry(),
                new OpsPrometheusQueryProtocolService(),
                OpsPrometheusSettings.defaults(),
                new OpsSubAgentRequestPolicy(),
                new OpsPrometheusResponseProjector());
    }

    @Autowired
    public PrometheusMetricOpsSubAgent(OpsSubAgentDecisionService decisionService,
                                       OpsRunCancellationRegistry cancellationRegistry,
                                       OpsPrometheusSettings settings) {
        this(decisionService,
                cancellationRegistry,
                new OpsPrometheusQueryProtocolService(),
                settings,
                new OpsSubAgentRequestPolicy(),
                new OpsPrometheusResponseProjector());
    }

    PrometheusMetricOpsSubAgent(
            OpsSubAgentDecisionService decisionService,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsPrometheusQueryProtocolService metricQueryService,
            OpsPrometheusSettings settings,
            OpsSubAgentRequestPolicy requestPolicy,
            OpsPrometheusResponseProjector responseProjector) {
        this.decisionService = decisionService;
        this.cancellationRegistry = cancellationRegistry;
        this.metricQueryService = metricQueryService;
        this.settings = settings == null ? OpsPrometheusSettings.defaults() : settings;
        this.requestPolicy = requestPolicy == null ? new OpsSubAgentRequestPolicy() : requestPolicy;
        this.responseProjector = responseProjector == null ? new OpsPrometheusResponseProjector() : responseProjector;
    }

    @Autowired
    void setAuthoritativeEvidenceProjector(OpsAuthoritativeDatasourceEvidenceProjector projector) {
        this.authoritativeEvidenceProjector = projector == null
                ? new OpsAuthoritativeDatasourceEvidenceProjector()
                : projector;
    }

    @Override
    public String source() {
        return SOURCE_PROM;
    }

    @Override
    public String agentId() {
        return "prometheus-agent";
    }

    @Override
    public String displayName() {
        return "Prometheus 指标子 Agent";
    }

    @Override
    public String capability() {
        return "查询 Prometheus 实例 UP、QPS、5xx 错误率、接口延迟、JVM、CPU 和资源趋势。";
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
            currentRequest = requestPolicy.expandPromWindow(currentRequest);
            attempts.add(attempt("LOOP iteration=" + (iteration + 1) + "/" + maxIterations
                            + ", nextPromWindow=" + currentRequest.getPromWindow(),
                    0,
                    "OBSERVE 后 Prometheus 子 Agent 自主扩大 rate 窗口并继续查询"));
        }

        return latestResult == null
                ? result(task, STATUS_ERROR, "Prometheus 指标子 Agent 未执行任何查询。", evidence, attempts, gaps, adjustments, false, 0D)
                : withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, true, maxIterations);
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO investigateOnce(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                                  OpsAgentRunRequestDTO request,
                                                                                  OpsAnalysisResponseDTO response,
                                                                                  OpsQuestionContext questionContext,
                                                                                  String previousObservation,
                                                                                  int iteration,
                                                                                  int maxIterations) {
        OpsSubAgentDecision decision = decisionService.decide(SOURCE_PROM, task, request, questionContext, """
                Prometheus 存放真实指标，适合查询 up、QPS、5xx 错误率、接口延迟、JVM、CPU 和资源趋势。
                不适合读取堆栈日志、traceId 明细或 SOP 文档。
                """, previousObservation);
        OpsAgentRunRequestDTO effectiveRequest = requestPolicy.applyPromWindowDecision(request, decision.promWindow());
        fetchMetricSummary(effectiveRequest, response, questionContext);
        OpsAnalysisResponseDTO.MetricSummaryDTO metrics = response.getMetricSummary();
        boolean available = Boolean.TRUE.equals(response.getPrometheusStatus().getAvailable());
        List<String> evidence = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = new ArrayList<>();
        attempts.add(attempt("THINK iteration=" + iteration + "/" + maxIterations
                        + ", queryFocus=" + value(decision.queryFocus())
                        + ", promWindow=" + value(decision.promWindow())
                        + ", reason=" + decision.reason(),
                0,
                decision.llmGenerated() ? "LLM prometheus-agent 生成查询策略" : "规则 prometheus-agent 查询策略"));
        attempts.add(attempt(
                "job=" + settings.jobName() + ", promWindow=" + effectiveRequest.getPromWindow() + ", uriFilter=" + value(questionContext.primaryUri()) + ", queries=up/qps/error_rate/latency/jvm/cpu",
                response.getEndpointMetrics() == null ? 0 : response.getEndpointMetrics().size(),
                available ? "Prometheus 查询成功" : response.getPrometheusStatus().getMessage()));

        if (!available) {
            gaps.add("Prometheus 当前不可用，无法获取指标证据。");
            adjustments.add("检查 Prometheus 容器、9090 端口和 " + settings.jobName() + " scrape target。");
            return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_BLOCKED, "Prometheus 指标子 Agent 被阻塞：" + response.getPrometheusStatus().getMessage(), false, 0D, iteration, maxIterations);
        }

        evidence.add("实例状态 " + metrics.getInstanceUp() + "/" + metrics.getInstanceTotal() + " UP。");
        evidence.add("总 QPS " + value(metrics.getTotalQps()) + "，5xx 错误率 " + value(metrics.getErrorRate()) + "%。");
        evidence.add("Heap 使用率 " + value(metrics.getHeapMemoryUsagePercent()) + "%，进程 CPU " + value(metrics.getProcessCpuUsagePercent()) + "%。");
        if (response.getEndpointMetrics() != null && !response.getEndpointMetrics().isEmpty()) {
            evidence.add("接口指标 Top：" + response.getEndpointMetrics().stream().limit(5)
                    .map(endpoint -> endpoint.getMethod() + " " + endpoint.getUri() + " status=" + endpoint.getStatus() + " qps=" + endpoint.getQps() + " avgMs=" + endpoint.getAvgResponseMs())
                    .collect(Collectors.joining("; ")));
        }

        boolean anomaly = responseProjector.hasAnomaly(metrics);
        if (!anomaly && (metrics.getTotalQps() == null || metrics.getTotalQps() <= 0D)) {
            gaps.add("当前窗口业务 QPS 接近 0，指标信息量有限。");
            adjustments.add("在业务流量发生时重查，或扩大 rate 窗口到 15m/30m。");
            return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_INSUFFICIENT, "Prometheus 可用，但当前窗口指标信号不足。", true, 0.45D, iteration, maxIterations);
        }

        return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_FOUND, anomaly ? "Prometheus 发现异常或风险指标。" : "Prometheus 未发现明显异常，指标可作为健康证据。", false, anomaly ? 0.85D : 0.7D, iteration, maxIterations);
    }

    public void collectMetricSummary(OpsAgentRunRequestDTO request,
                                     OpsAnalysisResponseDTO response,
                                     OpsQuestionContext questionContext) {
        fetchMetricSummary(request, response, questionContext);
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
        OpsAgentReview review = decisionService.review(SOURCE_PROM, task, request, questionContext, observation(status, summary, evidence, gaps, adjustments, iteration, maxIterations), fallback);
        if (review.llmGenerated()) {
            attempts.add(attempt("REFLECT status=" + review.status() + ", confidence=" + review.confidence(), evidence.size(), "LLM prometheus-agent 复盘真实指标 observation"));
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

    private void fetchMetricSummary(OpsAgentRunRequestDTO request, OpsAnalysisResponseDTO response, OpsQuestionContext questionContext) {
        try {
            assertNotCanceled(request);
            response.setPromWindow(request.getPromWindow());
            OpsPrometheusQueryProtocolService.Result queryResult = metricQueryService.execute(
                    new OpsPrometheusQueryProtocolService.Input(
                            settings.baseUrl(),
                            settings.jobName(),
                            settings.timeoutSeconds(),
                            request.getPromWindow(),
                            questionContext.primaryUri()),
                    () -> assertNotCanceled(request));

            OpsPrometheusResponseProjector.Projection projection = responseProjector.project(queryResult);
            response.setMetricSummary(projection.summary());
            response.setEndpointMetrics(projection.endpointMetrics());
            response.setPrometheusStatus(status("Prometheus", settings.baseUrl(), true,
                    "已读取真实监控指标，uriFilter=" + value(questionContext.primaryUri())));
            authoritativeEvidenceProjector.record(
                    request,
                    OpsAuthoritativeDatasourceEvidenceProjector.PROMETHEUS,
                    settings.baseUrl(),
                    OpsAuthoritativeDatasourceEvidenceProjector.summary(
                            "promWindow", value(request.getPromWindow()),
                            "metricSummary", projection.summary(),
                            "endpointMetrics", projection.endpointMetrics()));
            assertNotCanceled(request);
        } catch (Exception e) {
            log.warn("读取 Prometheus 指标失败：{}", e.getMessage());
            response.setPrometheusStatus(status("Prometheus", settings.baseUrl(), false, e.getMessage()));
        }
    }

    private void assertNotCanceled(OpsAgentRunRequestDTO request) {
        cancellationRegistry.assertNotCanceled(request == null ? null : request.getRunId());
    }

}
