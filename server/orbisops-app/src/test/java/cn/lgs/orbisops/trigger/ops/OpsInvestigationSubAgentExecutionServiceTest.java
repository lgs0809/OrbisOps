package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsInvestigationSubAgentExecutionServiceTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void registryKeepsFirstAgentForDuplicateSource() {
        OpsSubAgent first = agent("elasticsearch", (task, request, response, context) -> result(
                task,
                "FOUND",
                "first"));
        OpsSubAgent second = agent("elasticsearch", (task, request, response, context) -> result(
                task,
                "FOUND",
                "second"));
        OpsInvestigationSubAgentExecutionService service = service(List.of(first, second));

        OpsAnalysisResponseDTO.InvestigationResultDTO result = service.executeOne(
                task("elasticsearch", "es-log-agent"),
                request("run-1", 10, 12),
                new OpsAnalysisResponseDTO(),
                OpsQuestionContext.from("查日志"),
                12);

        assertEquals(Set.of("elasticsearch"), service.registeredSources());
        assertEquals("first", result.getSummary());
    }

    @Test
    void unknownSourceReturnsBlockedResult() {
        OpsInvestigationSubAgentExecutionService service = service(List.of());

        OpsAnalysisResponseDTO.InvestigationResultDTO result = service.executeOne(
                task("unknown", "unknown-agent"),
                request("run-2", 10, 12),
                new OpsAnalysisResponseDTO(),
                OpsQuestionContext.from("查未知源"),
                12);

        assertEquals("BLOCKED", result.getStatus());
        assertTrue(result.getSummary().contains("未知数据源"));
        assertEquals(List.of("主 Agent 生成了未注册的数据源。"), result.getGaps());
        assertFalse(Boolean.TRUE.equals(result.getShouldRetry()));
    }

    @Test
    void agentExceptionReturnsBlockedResult() {
        OpsSubAgent broken = agent("elasticsearch", (task, request, response, context) -> {
            throw new IllegalStateException("boom");
        });
        OpsInvestigationSubAgentExecutionService service = service(List.of(broken));

        OpsAnalysisResponseDTO.InvestigationResultDTO result = service.executeOne(
                task("elasticsearch", "es-log-agent"),
                request("run-3", 10, 12),
                new OpsAnalysisResponseDTO(),
                OpsQuestionContext.from("查日志"),
                12);

        assertEquals("BLOCKED", result.getStatus());
        assertTrue(result.getSummary().contains("boom"));
        assertEquals(0D, result.getConfidence());
    }

    @Test
    void evidenceBudgetTrimsAllBoundedCollections() {
        OpsSubAgent verbose = agent("elasticsearch", (task, request, response, context) ->
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(task.getSource())
                        .agent(task.getAgent())
                        .status("FOUND")
                        .summary("verbose")
                        .evidence(List.of("e1", "e2", "e3"))
                        .gaps(List.of("g1", "g2", "g3"))
                        .suggestedAdjustments(List.of("a1", "a2", "a3"))
                        .attempts(List.of(
                                attempt("q1"),
                                attempt("q2"),
                                attempt("q3")))
                        .build());
        OpsInvestigationSubAgentExecutionService service = service(List.of(verbose));

        OpsAnalysisResponseDTO.InvestigationResultDTO result = service.executeOne(
                task("elasticsearch", "es-log-agent"),
                request("run-4", 10, 2),
                new OpsAnalysisResponseDTO(),
                OpsQuestionContext.from("查日志"),
                12);

        assertEquals(List.of("e1", "e2"), result.getEvidence());
        assertEquals(List.of("g1", "g2"), result.getGaps());
        assertEquals(List.of("a1", "a2"), result.getSuggestedAdjustments());
        assertEquals(2, result.getAttempts().size());
    }

    @Test
    void elasticsearchFragmentMergesOnlyElasticsearchProjection() {
        OpsSubAgent es = agent("elasticsearch", (task, request, response, context) -> {
            response.setRangeMinutes(60);
            response.setElasticsearchStatus(status("Elasticsearch", "ok"));
            response.setLogSummary(OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                    .totalLogs(9L)
                    .build());
            response.setRecentLogs(List.of(OpsAnalysisResponseDTO.LogSampleDTO.builder()
                    .message("error")
                    .build()));
            response.setMetricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                    .instanceTotal(999)
                    .build());
            return result(task, "FOUND", "logs found");
        });
        OpsInvestigationSubAgentExecutionService service = service(List.of(es));
        OpsAnalysisResponseDTO shared = OpsAnalysisResponseDTO.builder()
                .metricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                        .instanceTotal(1)
                        .build())
                .build();

        service.executeOne(
                task("elasticsearch", "es-log-agent"),
                request("run-5", 10, 12),
                shared,
                OpsQuestionContext.from("查日志"),
                12);

        assertEquals(60, shared.getRangeMinutes());
        assertEquals("ok", shared.getElasticsearchStatus().getMessage());
        assertEquals(9L, shared.getLogSummary().getTotalLogs());
        assertEquals("error", shared.getRecentLogs().get(0).getMessage());
        assertEquals(1, shared.getMetricSummary().getInstanceTotal());
    }

    @Test
    void parallelBatchMergesPrometheusAndMysqlFragments() {
        OpsSubAgent prom = agent("prometheus", (task, request, response, context) -> {
            response.setPromWindow("15m");
            response.setPrometheusStatus(status("Prometheus", "prom-ok"));
            response.setMetricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                    .instanceTotal(3)
                    .build());
            response.setEndpointMetrics(List.of(OpsAnalysisResponseDTO.EndpointMetricDTO.builder()
                    .uri("/api/orders")
                    .build()));
            return result(task, "FOUND", "metrics found");
        });
        OpsSubAgent mysql = agent("mysql_slow_sql", (task, request, response, context) -> {
            response.setMysqlSlowSqlStatus(status("MySQL Slow SQL", "mysql-ok"));
            response.setSlowSqlSummary(OpsAnalysisResponseDTO.SlowSqlSummaryDTO.builder()
                    .slowStatements(2L)
                    .build());
            response.setSlowSqlSamples(List.of(OpsAnalysisResponseDTO.SlowSqlSampleDTO.builder()
                    .digest("digest-1")
                    .build()));
            return result(task, "FOUND", "slow sql found");
        });
        OpsInvestigationSubAgentExecutionService service = service(List.of(prom, mysql));
        OpsAnalysisResponseDTO shared = new OpsAnalysisResponseDTO();

        List<OpsInvestigationSubAgentExecutionService.TaskExecution> executions = service.executeBatch(
                List.of(
                        task("prometheus", "prometheus-agent"),
                        task("mysql_slow_sql", "mysql-slow-sql-agent")),
                request("run-6", 10, 12),
                shared,
                OpsQuestionContext.from("检查指标和慢 SQL"),
                12);

        assertEquals(2, executions.size());
        assertEquals("15m", shared.getPromWindow());
        assertEquals("prom-ok", shared.getPrometheusStatus().getMessage());
        assertEquals(3, shared.getMetricSummary().getInstanceTotal());
        assertEquals("/api/orders", shared.getEndpointMetrics().get(0).getUri());
        assertEquals("mysql-ok", shared.getMysqlSlowSqlStatus().getMessage());
        assertEquals(2L, shared.getSlowSqlSummary().getSlowStatements());
        assertEquals("digest-1", shared.getSlowSqlSamples().get(0).getDigest());
    }

    @Test
    void singleSourceBatchUsesTheSameNodeTimeoutBudgetAsParallelBatch() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            OpsSubAgent slow = agent("prometheus", (task, request, response, context) -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return result(task, "FOUND", "late");
            });
            OpsInvestigationSubAgentExecutionService service =
                    new OpsInvestigationSubAgentExecutionService(
                            List.of(slow), executor, new OpsRunCancellationRegistry());
            long started = System.nanoTime();

            List<OpsInvestigationSubAgentExecutionService.TaskExecution> executions = service.executeBatch(
                    List.of(task("prometheus", "prometheus-agent")),
                    request("run-timeout", 1, 12),
                    new OpsAnalysisResponseDTO(),
                    OpsQuestionContext.from("查指标"),
                    12);

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            assertEquals(1, executions.size());
            assertEquals("BLOCKED", executions.get(0).result().getStatus());
            assertTrue(executions.get(0).result().getSummary().contains("超过预算 1s"));
            assertTrue(elapsedMs < 2500L, "single-source execution must not bypass node timeout");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancellationPropagatesBeforeSubAgentInvocation() {
        OpsRunCancellationRegistry registry = new OpsRunCancellationRegistry();
        final boolean[] invoked = {false};
        OpsSubAgent agent = agent("elasticsearch", (task, request, response, context) -> {
            invoked[0] = true;
            return result(task, "FOUND", "should not run");
        });
        OpsInvestigationSubAgentExecutionService service =
                new OpsInvestigationSubAgentExecutionService(
                        List.of(agent),
                        DIRECT_EXECUTOR,
                        registry);
        registry.markCanceled("run-canceled");

        assertThrows(
                OpsRunCanceledException.class,
                () -> service.executeOne(
                        task("elasticsearch", "es-log-agent"),
                        request("run-canceled", 10, 12),
                        new OpsAnalysisResponseDTO(),
                        OpsQuestionContext.from("查日志"),
                        12));
        assertFalse(invoked[0]);
    }

    @Test
    void executeOneReturnsTheAgentResultInstance() {
        OpsAnalysisResponseDTO.InvestigationResultDTO expected =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("rag")
                        .agent("rag-knowledge-agent")
                        .status("FOUND")
                        .summary("knowledge")
                        .build();
        OpsSubAgent rag = agent("rag", (task, request, response, context) -> expected);

        OpsAnalysisResponseDTO.InvestigationResultDTO actual = service(List.of(rag)).executeOne(
                task("rag", "rag-knowledge-agent"),
                request("run-7", 10, 12),
                new OpsAnalysisResponseDTO(),
                OpsQuestionContext.from("查 SOP"),
                12);

        assertSame(expected, actual);
    }

    private OpsInvestigationSubAgentExecutionService service(List<OpsSubAgent> agents) {
        return new OpsInvestigationSubAgentExecutionService(
                agents,
                DIRECT_EXECUTOR,
                new OpsRunCancellationRegistry());
    }

    private OpsSubAgent agent(String source, InvestigationAction action) {
        return new OpsSubAgent() {
            @Override
            public String source() {
                return source;
            }

            @Override
            public OpsAnalysisResponseDTO.InvestigationResultDTO investigate(
                    OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                    OpsAgentRunRequestDTO request,
                    OpsAnalysisResponseDTO response,
                    OpsQuestionContext questionContext) {
                return action.execute(task, request, response, questionContext);
            }
        };
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source, String agent) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(agent)
                .goal("goal")
                .reason("reason")
                .priority(1)
                .build();
    }

    private OpsAgentRunRequestDTO request(
            String runId,
            int timeoutSeconds,
            int maxEvidenceItems) {
        return OpsAgentRunRequestDTO.builder()
                .runId(runId)
                .nodeTimeoutSeconds(timeoutSeconds)
                .maxEvidenceItems(maxEvidenceItems)
                .build();
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO result(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            String status,
            String summary) {
        return OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                .source(task.getSource())
                .agent(task.getAgent())
                .status(status)
                .summary(summary)
                .evidence(List.of())
                .attempts(List.of())
                .gaps(List.of())
                .suggestedAdjustments(List.of())
                .shouldRetry(false)
                .confidence(1D)
                .build();
    }

    private OpsAnalysisResponseDTO.InvestigationAttemptDTO attempt(String query) {
        return OpsAnalysisResponseDTO.InvestigationAttemptDTO.builder()
                .query(query)
                .build();
    }

    private OpsAnalysisResponseDTO.DataSourceStatusDTO status(String name, String message) {
        return OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .name(name)
                .message(message)
                .available(true)
                .build();
    }

    @FunctionalInterface
    private interface InvestigationAction {
        OpsAnalysisResponseDTO.InvestigationResultDTO execute(
                OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                OpsAgentRunRequestDTO request,
                OpsAnalysisResponseDTO response,
                OpsQuestionContext questionContext);
    }
}
