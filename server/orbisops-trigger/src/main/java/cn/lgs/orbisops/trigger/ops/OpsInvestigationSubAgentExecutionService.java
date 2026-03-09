package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_ES;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_PROM;

/**
 * Reliable execution boundary for datasource SubAgents.
 *
 * <p>Owns concurrency, timeout, cancellation, trace propagation, response
 * isolation/merge and evidence budgets. Investigation routing policy remains
 * outside this class.</p>
 */
final class OpsInvestigationSubAgentExecutionService {

    private static final String STATUS_BLOCKED = "BLOCKED";

    private final Map<String, OpsSubAgent> subAgents;
    private final Executor executor;
    private final OpsRunCancellationRegistry cancellationRegistry;

    OpsInvestigationSubAgentExecutionService(List<OpsSubAgent> subAgents,
                                             Executor executor,
                                             OpsRunCancellationRegistry cancellationRegistry) {
        if (executor == null) {
            throw new IllegalArgumentException("INVESTIGATION_SUB_AGENT_EXECUTOR_REQUIRED");
        }
        if (cancellationRegistry == null) {
            throw new IllegalArgumentException("INVESTIGATION_CANCELLATION_REGISTRY_REQUIRED");
        }
        this.subAgents = Optional.ofNullable(subAgents).orElse(List.of()).stream()
                .collect(Collectors.toMap(
                        OpsSubAgent::source,
                        agent -> agent,
                        (left, right) -> left));
        this.executor = executor;
        this.cancellationRegistry = cancellationRegistry;
    }

    Set<String> registeredSources() {
        return java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(subAgents.keySet()));
    }

    void assertNotCanceled(OpsAgentRunRequestDTO request) {
        cancellationRegistry.assertNotCanceled(request == null ? null : request.getRunId());
    }

    List<TaskExecution> executeBatch(
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> batch,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            int defaultMaxEvidenceItems) {
        if (batch == null || batch.isEmpty()) return List.of();
        List<PendingExecution> pendingExecutions = batch.stream()
                .map(task -> pending(
                        task,
                        request,
                        response,
                        questionContext,
                        defaultMaxEvidenceItems))
                .toList();
        List<IsolatedExecution> executions = new ArrayList<>();
        try {
            for (PendingExecution pending : pendingExecutions) {
                executions.add(await(pending, request));
            }
        } catch (OpsRunCanceledException e) {
            pendingExecutions.forEach(pending -> pending.future().cancel(true));
            throw e;
        }
        executions.forEach(execution -> merge(execution, response));
        return executions.stream().map(IsolatedExecution::publicView).toList();
    }

    OpsAnalysisResponseDTO.InvestigationResultDTO executeOne(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            int defaultMaxEvidenceItems) {
        IsolatedExecution execution = await(
                pending(task, request, response, questionContext, defaultMaxEvidenceItems),
                request);
        merge(execution, response);
        return execution.result();
    }

    private PendingExecution pending(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            int defaultMaxEvidenceItems) {
        java.util.function.Supplier<IsolatedExecution> traced = OpsLlmTraceContext.wrap(
                subAgentTrace(task),
                () -> executeIsolated(
                        task,
                        request,
                        response,
                        questionContext,
                        defaultMaxEvidenceItems));
        FutureTask<IsolatedExecution> future = new FutureTask<>(traced::get);
        executor.execute(future);
        return new PendingExecution(task, future);
    }

    private IsolatedExecution executeIsolated(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            int defaultMaxEvidenceItems) {
        OpsAnalysisResponseDTO responseFragment = fork(response);
        OpsAnalysisResponseDTO.InvestigationResultDTO result = trimResult(
                executeSafely(task, request, responseFragment, questionContext),
                request,
                defaultMaxEvidenceItems);
        return new IsolatedExecution(task, result, responseFragment);
    }

    private IsolatedExecution await(PendingExecution pending,
                                    OpsAgentRunRequestDTO request) {
        try {
            assertNotCanceled(request);
            return pending.future().get(taskTimeoutSeconds(request), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.future().cancel(true);
            return new IsolatedExecution(
                    pending.task(),
                    blockedResult(
                            pending.task(),
                            "子 Agent 执行被取消。",
                            "主任务线程被中断。",
                            "停止该数据源查询。"),
                    null);
        } catch (TimeoutException e) {
            pending.future().cancel(true);
            return new IsolatedExecution(
                    pending.task(),
                    blockedResult(
                            pending.task(),
                            "子 Agent 执行超过预算 " + taskTimeoutSeconds(request) + "s，已停止等待。",
                            "该数据源查询超出节点预算。",
                            "收紧查询条件、降低样本量或检查数据源响应时间。"),
                    null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof OpsRunCanceledException canceled) {
                throw canceled;
            }
            return new IsolatedExecution(
                    pending.task(),
                    blockedResult(
                            pending.task(),
                            "子 Agent 执行异常：" + value(cause.getMessage()),
                            "子 Agent 执行时抛出异常。",
                            "检查子 Agent 查询参数和数据源连通性。"),
                    null);
        }
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO executeSafely(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext) {
        try {
            return execute(task, request, response, questionContext);
        } catch (OpsRunCanceledException e) {
            throw e;
        } catch (CompletionException e) {
            return blockedResult(
                    task,
                    "子 Agent 执行异常：" + value(
                            e.getCause() == null
                                    ? e.getMessage()
                                    : e.getCause().getMessage()),
                    "子 Agent 执行时抛出异常。",
                    "检查子 Agent 查询参数和数据源连通性。");
        } catch (Exception e) {
            return blockedResult(
                    task,
                    "子 Agent 执行异常：" + value(e.getMessage()),
                    "子 Agent 执行时抛出异常。",
                    "检查子 Agent 查询参数和数据源连通性。");
        }
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO execute(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext) {
        OpsSubAgent subAgent = subAgents.get(task.getSource());
        if (subAgent == null) {
            return blockedResult(
                    task,
                    "未知数据源：" + task.getSource(),
                    "主 Agent 生成了未注册的数据源。",
                    "忽略该数据源，重新规划。");
        }
        assertNotCanceled(request);
        return subAgent.investigate(task, request, response, questionContext);
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO trimResult(
            OpsAnalysisResponseDTO.InvestigationResultDTO result,
            OpsAgentRunRequestDTO request,
            int defaultMaxEvidenceItems) {
        if (result == null) return null;
        Integer requestedLimit = request == null ? null : request.getMaxEvidenceItems();
        int limit = Math.max(1, Math.min(
                Optional.ofNullable(requestedLimit).orElse(defaultMaxEvidenceItems),
                50));
        result.setEvidence(limit(result.getEvidence(), limit));
        result.setGaps(limit(result.getGaps(), limit));
        result.setSuggestedAdjustments(limit(result.getSuggestedAdjustments(), limit));
        result.setAttempts(limit(result.getAttempts(), limit));
        return result;
    }

    private <T> List<T> limit(List<T> values, int limit) {
        if (values == null || values.size() <= limit) return values;
        return new ArrayList<>(values.subList(0, limit));
    }

    private long taskTimeoutSeconds(OpsAgentRunRequestDTO request) {
        Integer configured = request == null ? null : request.getNodeTimeoutSeconds();
        return Math.max(1L, Math.min(
                configured == null ? 60L : configured.longValue(),
                300L));
    }

    private OpsLlmTraceContext.Trace subAgentTrace(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task) {
        OpsLlmTraceContext.Trace parentTrace = OpsLlmTraceContext.current();
        if (parentTrace == null || task == null) return parentTrace;
        return parentTrace.child(
                "SUB_AGENT:" + value(task.getAgent()),
                "sub-agent-" + value(task.getSource()),
                "SUB_AGENT",
                task.getAgent(),
                task.getSource());
    }

    private OpsAnalysisResponseDTO fork(OpsAnalysisResponseDTO response) {
        synchronized (response) {
            return OpsAnalysisResponseDTO.builder()
                    .analysisId(response.getAnalysisId())
                    .agentDefinitionId(response.getAgentDefinitionId())
                    .agentVersion(response.getAgentVersion())
                    .agentRuntime(response.getAgentRuntime())
                    .rangeMinutes(response.getRangeMinutes())
                    .promWindow(response.getPromWindow())
                    .generatedAt(response.getGeneratedAt())
                    .elasticsearchStatus(response.getElasticsearchStatus())
                    .prometheusStatus(response.getPrometheusStatus())
                    .mysqlSlowSqlStatus(response.getMysqlSlowSqlStatus())
                    .logSummary(response.getLogSummary())
                    .metricSummary(response.getMetricSummary())
                    .slowSqlSummary(response.getSlowSqlSummary())
                    .endpointMetrics(response.getEndpointMetrics())
                    .recentLogs(response.getRecentLogs())
                    .slowSqlSamples(response.getSlowSqlSamples())
                    .investigationPlan(response.getInvestigationPlan())
                    .build();
        }
    }

    private void merge(IsolatedExecution execution,
                       OpsAnalysisResponseDTO response) {
        if (execution == null
                || execution.task() == null
                || execution.responseFragment() == null) {
            return;
        }
        OpsAnalysisResponseDTO.InvestigationTaskDTO task = execution.task();
        OpsAnalysisResponseDTO fragment = execution.responseFragment();
        synchronized (response) {
            switch (task.getSource()) {
                case SOURCE_ES -> {
                    if (fragment.getRangeMinutes() != null) {
                        response.setRangeMinutes(fragment.getRangeMinutes());
                    }
                    if (fragment.getElasticsearchStatus() != null) {
                        response.setElasticsearchStatus(fragment.getElasticsearchStatus());
                    }
                    if (fragment.getLogSummary() != null) {
                        response.setLogSummary(fragment.getLogSummary());
                    }
                    if (fragment.getRecentLogs() != null) {
                        response.setRecentLogs(fragment.getRecentLogs());
                    }
                }
                case SOURCE_PROM -> {
                    if (fragment.getPromWindow() != null) {
                        response.setPromWindow(fragment.getPromWindow());
                    }
                    if (fragment.getPrometheusStatus() != null) {
                        response.setPrometheusStatus(fragment.getPrometheusStatus());
                    }
                    if (fragment.getMetricSummary() != null) {
                        response.setMetricSummary(fragment.getMetricSummary());
                    }
                    if (fragment.getEndpointMetrics() != null) {
                        response.setEndpointMetrics(fragment.getEndpointMetrics());
                    }
                }
                case SOURCE_MYSQL_SLOW_SQL -> {
                    if (fragment.getMysqlSlowSqlStatus() != null) {
                        response.setMysqlSlowSqlStatus(fragment.getMysqlSlowSqlStatus());
                    }
                    if (fragment.getSlowSqlSummary() != null) {
                        response.setSlowSqlSummary(fragment.getSlowSqlSummary());
                    }
                    if (fragment.getSlowSqlSamples() != null) {
                        response.setSlowSqlSamples(fragment.getSlowSqlSamples());
                    }
                }
                default -> {
                }
            }
        }
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO blockedResult(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            String summary,
            String gap,
            String adjustment) {
        return OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                .source(task.getSource())
                .agent(task.getAgent())
                .status(STATUS_BLOCKED)
                .summary(summary)
                .evidence(new ArrayList<>())
                .attempts(new ArrayList<>())
                .gaps(List.of(gap))
                .suggestedAdjustments(List.of(adjustment))
                .shouldRetry(false)
                .confidence(0D)
                .build();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record TaskExecution(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                         OpsAnalysisResponseDTO.InvestigationResultDTO result) {
    }

    private record IsolatedExecution(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAnalysisResponseDTO.InvestigationResultDTO result,
            OpsAnalysisResponseDTO responseFragment) {
        private TaskExecution publicView() {
            return new TaskExecution(task, result);
        }
    }

    private record PendingExecution(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            Future<IsolatedExecution> future) {
    }
}
