package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;
import cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Executes bounded replan sub-agent tasks and projects timeout/error outcomes. */
final class OpsAnalysisReplanTaskRunner {

    private final OpsInvestigationExecutor investigationExecutor;
    private final OpsAnalysisRuntimeStateManager stateManager;
    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final Executor subAgentExecutor;

    OpsAnalysisReplanTaskRunner(
            OpsInvestigationExecutor investigationExecutor,
            OpsAnalysisRuntimeStateManager stateManager,
            OpsAnalysisRoutingPolicy routingPolicy,
            Executor subAgentExecutor) {
        this.investigationExecutor = investigationExecutor;
        this.stateManager = stateManager;
        this.routingPolicy = routingPolicy;
        this.subAgentExecutor = subAgentExecutor;
    }

    List<OpsAnalysisResponseDTO.InvestigationResultDTO> execute(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            int round,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults) {
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks =
                routingPolicy.canonicalUnexecutedTasks(
                        plan, existingResults, definition);
        if (tasks.isEmpty()) return List.of();

        OpsLlmTraceContext.Trace parentTrace = OpsLlmTraceContext.current();
        List<CompletableFuture<SubAgentExecution>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        OpsLlmTraceContext.wrap(
                                taskTrace(
                                        parentTrace,
                                        task,
                                        "main-replan-r"
                                                + round
                                                + "-"
                                                + value(task.getSource())),
                                () -> {
                                    stateManager.assertNotCanceled(request);
                                    String startedAt = stateManager.now();
                                    long startedMillis = System.currentTimeMillis();
                                    OpsAnalysisResponseDTO.InvestigationResultDTO result =
                                            investigationExecutor.executeGraphSubAgent(
                                                    task,
                                                    request,
                                                    response,
                                                    questionContext);
                                    return new SubAgentExecution(
                                            task,
                                            result,
                                            startedAt,
                                            startedMillis);
                                }),
                        subAgentExecutor))
                .toList();

        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results =
                new ArrayList<>();
        try {
            for (int index = 0; index < futures.size(); index++) {
                SubAgentExecution execution = await(
                        tasks.get(index), futures.get(index), request);
                OpsAnalysisResponseDTO.InvestigationResultDTO result =
                        execution.result();
                if (result == null) continue;
                results.add(result);
                OpsWorkflowNode node = OpsWorkflowNode.builder()
                        .nodeId("main-replan-r"
                                + round
                                + "-"
                                + value(result.getSource()))
                        .type("SUB_AGENT")
                        .agent(execution.task().getAgent())
                        .description("主 Agent 第 " + round + " 轮重规划追加的子 Agent 查询。")
                        .build();
                stateManager.recordStep(
                        steps,
                        request,
                        response,
                        node,
                        "SUB_AGENT",
                        value(result.getStatus()),
                        execution.task().getAgent()
                                + " 第 "
                                + round
                                + " 轮返回："
                                + value(result.getSummary()),
                        execution.startedAt(),
                        execution.startedMillis());
            }
        } catch (OpsRunCanceledException error) {
            futures.forEach(future -> future.cancel(true));
            throw error;
        }
        return results;
    }

    void waitForInitialSubAgents(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults,
            OpsAgentRunRequestDTO request) {
        Set<String> expectedSources = Optional.ofNullable(plan.getTasks())
                .orElse(List.of()).stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .filter(StringUtils::hasText)
                .map(routingPolicy::normalizeSource)
                .collect(Collectors.toSet());
        if (expectedSources.isEmpty()) return;
        long deadline = System.currentTimeMillis()
                + nodeTimeoutSeconds(request) * 1000L;
        while (System.currentTimeMillis() < deadline) {
            stateManager.assertNotCanceled(request);
            Set<String> completedSources;
            synchronized (initialResults) {
                completedSources = initialResults.stream()
                        .map(OpsAnalysisResponseDTO.InvestigationResultDTO::getSource)
                        .filter(StringUtils::hasText)
                        .map(routingPolicy::normalizeSource)
                        .collect(Collectors.toSet());
            }
            if (completedSources.containsAll(expectedSources)) return;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private SubAgentExecution await(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            CompletableFuture<SubAgentExecution> future,
            OpsAgentRunRequestDTO request) {
        try {
            stateManager.assertNotCanceled(request);
            return future.get(nodeTimeoutSeconds(request), TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return blocked(
                    task,
                    "主 Agent 重规划子 Agent 被取消。",
                    "主任务线程被中断。",
                    "停止该数据源查询。");
        } catch (TimeoutException error) {
            future.cancel(true);
            return blocked(
                    task,
                    "主 Agent 重规划子 Agent 超过预算 "
                            + nodeTimeoutSeconds(request)
                            + "s，已停止等待。",
                    "该数据源查询超出节点预算。",
                    "收紧查询条件、降低样本量或检查数据源响应时间。");
        } catch (ExecutionException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            return blocked(
                    task,
                    "主 Agent 重规划子 Agent 异常：" + value(cause.getMessage()),
                    "子 Agent 执行时抛出异常。",
                    "检查子 Agent 查询参数和数据源连通性。");
        }
    }

    private SubAgentExecution blocked(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            String summary,
            String gap,
            String adjustment) {
        OpsAnalysisResponseDTO.InvestigationResultDTO result =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(task.getSource())
                        .agent(task.getAgent())
                        .status("BLOCKED")
                        .summary(summary)
                        .evidence(new ArrayList<>())
                        .attempts(new ArrayList<>())
                        .gaps(List.of(gap))
                        .suggestedAdjustments(List.of(adjustment))
                        .shouldRetry(false)
                        .confidence(0D)
                        .build();
        return new SubAgentExecution(
                task,
                result,
                stateManager.now(),
                System.currentTimeMillis());
    }

    private OpsLlmTraceContext.Trace taskTrace(
            OpsLlmTraceContext.Trace parentTrace,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            String nodeId) {
        if (parentTrace == null || task == null) return parentTrace;
        return parentTrace.child(
                "SUB_AGENT:" + value(task.getAgent()),
                nodeId,
                "SUB_AGENT",
                task.getAgent(),
                task.getSource());
    }

    private long nodeTimeoutSeconds(OpsAgentRunRequestDTO request) {
        Integer configured = request == null
                ? null
                : request.getNodeTimeoutSeconds();
        return Math.max(
                1L,
                Math.min(configured == null ? 60L : configured.longValue(), 300L));
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record SubAgentExecution(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAnalysisResponseDTO.InvestigationResultDTO result,
            String startedAt,
            long startedMillis) {
    }
}
