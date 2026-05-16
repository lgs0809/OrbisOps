package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Publishes analysis run/node lifecycle events and enforces cancellation. */
final class OpsAnalysisRuntimeEventRecorder {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GraphEventApplicationService graphEventService;
    private final OpsRunCancellationRegistry cancellationRegistry;

    OpsAnalysisRuntimeEventRecorder(
            GraphEventApplicationService graphEventService,
            OpsRunCancellationRegistry cancellationRegistry) {
        if (graphEventService == null) {
            throw new IllegalArgumentException("GRAPH_EVENT_SERVICE_REQUIRED");
        }
        if (cancellationRegistry == null) {
            throw new IllegalArgumentException("RUN_CANCELLATION_REGISTRY_REQUIRED");
        }
        this.graphEventService = graphEventService;
        this.cancellationRegistry = cancellationRegistry;
    }

    void publishRunStarted(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response) {
        assertNotCanceled(request);
        graphEventService.publishRunEvent(
                request.getRunId(),
                response.getAnalysisId(),
                "RUN_STARTED",
                "RUNNING",
                "通用 Runtime 运维 Graph 开始执行：" + definition.getAgentId());
    }

    void publishRunFinished(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            String status,
            String summary) {
        graphEventService.publishRunEvent(
                request.getRunId(),
                response.getAnalysisId(),
                "RUN_FINISHED",
                status,
                StringUtils.hasText(summary)
                        ? summary
                        : "通用 Runtime 运维 Graph 执行结束：" + definition.getAgentId());
    }

    void recordStep(
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsWorkflowNode node,
            String nodeType,
            String status,
            String summary,
            String startedAt,
            long startedMillis) {
        long durationMs = Math.max(0L, System.currentTimeMillis() - startedMillis);
        String finishedAt = now();
        OpsAnalysisResponseDTO.AgentExecutionStepDTO step =
                OpsAnalysisResponseDTO.AgentExecutionStepDTO.builder()
                        .nodeId(node.getNodeId())
                        .nodeType(nodeType)
                        .agent(node.getAgent())
                        .source(null)
                        .status(status)
                        .summary(summary)
                        .startedAt(startedAt)
                        .finishedAt(finishedAt)
                        .durationMs(durationMs)
                        .build();
        steps.add(step);
        graphEventService.publish(
                request.getRunId(),
                response.getAnalysisId(),
                "NODE_FINISHED",
                node,
                status,
                summary,
                startedAt,
                finishedAt,
                durationMs,
                Map.of("runtime", "generic"));
        Integer timeoutSeconds = request.getNodeTimeoutSeconds();
        if (timeoutSeconds != null && durationMs > timeoutSeconds * 1000L) {
            graphEventService.publish(
                    request.getRunId(),
                    response.getAnalysisId(),
                    "NODE_BUDGET_EXCEEDED",
                    node,
                    "WARN",
                    "节点执行耗时 " + durationMs
                            + "ms，超过预算 " + timeoutSeconds + "s。",
                    startedAt,
                    finishedAt,
                    durationMs,
                    Map.of("runtime", "generic"));
        }
    }

    void recordFollowUpSteps(
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            int initialSize,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> safeResults =
                results == null ? List.of() : results;
        if (safeResults.size() <= initialSize) {
            return;
        }
        for (int i = initialSize; i < safeResults.size(); i++) {
            OpsAnalysisResponseDTO.InvestigationResultDTO result = safeResults.get(i);
            OpsWorkflowNode followUpNode = OpsWorkflowNode.builder()
                    .nodeId("follow-up-" + value(result.getSource())
                            + "-" + (i - initialSize + 1))
                    .type("FOLLOW_UP_SUB_AGENT")
                    .agent(result.getAgent())
                    .description("主 Agent 复盘后动态追加的子 Agent 查询。")
                    .build();
            String startedAt = now();
            recordStep(
                    steps,
                    request,
                    response,
                    followUpNode,
                    "FOLLOW_UP_SUB_AGENT",
                    value(result.getStatus()),
                    result.getAgent() + " follow-up 返回："
                            + value(result.getSummary()),
                    startedAt,
                    System.currentTimeMillis());
        }
    }

    void assertNotCanceled(OpsAgentRunRequestDTO request) {
        cancellationRegistry.assertNotCanceled(
                request == null ? null : request.getRunId());
    }

    String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
