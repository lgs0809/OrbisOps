package cn.lgs.orbisops.domain.runtime.workflow.model;

import java.time.Instant;

public record DurableWorkflowNodeState(
        String nodeId,
        DurableWorkflowNodeStatus status,
        int attempt,
        String outputHash,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {

    public DurableWorkflowNodeState {
        nodeId = required(nodeId, "DURABLE_WORKFLOW_NODE_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_NODE_STATUS_REQUIRED");
        if (attempt < 0) throw new IllegalArgumentException("DURABLE_WORKFLOW_NODE_ATTEMPT_INVALID");
        outputHash = text(outputHash);
        errorCode = text(errorCode);
        errorMessage = text(errorMessage);
    }

    public DurableWorkflowNodeState withStatus(
            DurableWorkflowNodeStatus next,
            int nextAttempt,
            String nextOutputHash,
            String nextErrorCode,
            String nextErrorMessage,
            Instant nextStartedAt,
            Instant nextFinishedAt) {
        return new DurableWorkflowNodeState(
                nodeId, next, nextAttempt, nextOutputHash, nextErrorCode,
                nextErrorMessage, nextStartedAt, nextFinishedAt);
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
