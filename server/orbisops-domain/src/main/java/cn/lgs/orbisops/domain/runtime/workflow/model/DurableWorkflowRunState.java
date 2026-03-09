package cn.lgs.orbisops.domain.runtime.workflow.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DurableWorkflowRunState(
        String runId,
        String projectId,
        String planHash,
        String definitionHash,
        String contextBundleHash,
        DurableWorkflowRunStatus status,
        String currentNodeId,
        Map<String, DurableWorkflowNodeState> nodeStates,
        List<DurableWorkflowRouteDecision> routeHistory,
        Map<String, Integer> loopCounters,
        Map<String, Object> variables,
        DurableWorkflowWaitState waitState,
        String errorCode,
        String errorMessage,
        Instant updatedAt
) {

    public DurableWorkflowRunState {
        runId = required(runId, "DURABLE_WORKFLOW_RUN_ID_REQUIRED");
        projectId = text(projectId);
        planHash = required(planHash, "DURABLE_WORKFLOW_PLAN_HASH_REQUIRED");
        definitionHash = required(definitionHash, "DURABLE_WORKFLOW_DEFINITION_HASH_REQUIRED");
        contextBundleHash = required(contextBundleHash, "DURABLE_WORKFLOW_CONTEXT_HASH_REQUIRED");
        if (status == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_STATUS_REQUIRED");
        currentNodeId = text(currentNodeId);
        nodeStates = immutableNodeStates(nodeStates);
        routeHistory = routeHistory == null ? List.of() : List.copyOf(routeHistory);
        loopCounters = immutableCounters(loopCounters);
        variables = immutableVariables(variables);
        waitState = waitState == null ? DurableWorkflowWaitState.none() : waitState;
        errorCode = text(errorCode);
        errorMessage = text(errorMessage);
        if (updatedAt == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_UPDATED_AT_REQUIRED");
    }

    public DurableWorkflowNodeState node(String nodeId) {
        DurableWorkflowNodeState state = nodeStates.get(text(nodeId));
        if (state == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_NODE_UNKNOWN:" + text(nodeId));
        return state;
    }

    private static Map<String, DurableWorkflowNodeState> immutableNodeStates(
            Map<String, DurableWorkflowNodeState> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, Integer> immutableCounters(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(text(key), value == null ? 0 : Math.max(0, value)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> immutableVariables(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
