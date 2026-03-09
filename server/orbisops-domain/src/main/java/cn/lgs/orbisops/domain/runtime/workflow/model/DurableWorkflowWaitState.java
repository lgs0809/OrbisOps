package cn.lgs.orbisops.domain.runtime.workflow.model;

import java.time.Instant;

public record DurableWorkflowWaitState(
        DurableWorkflowWaitType type,
        String nodeId,
        String tokenHash,
        Instant requestedAt,
        Instant expiresAt,
        Instant resumedAt,
        String decision
) {

    public DurableWorkflowWaitState {
        type = type == null ? DurableWorkflowWaitType.NONE : type;
        nodeId = text(nodeId);
        tokenHash = text(tokenHash);
        decision = text(decision);
        if (type != DurableWorkflowWaitType.NONE) {
            if (nodeId.isBlank()) throw new IllegalArgumentException("DURABLE_WORKFLOW_WAIT_NODE_REQUIRED");
            if (tokenHash.isBlank()) throw new IllegalArgumentException("DURABLE_WORKFLOW_WAIT_TOKEN_REQUIRED");
            if (requestedAt == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_WAIT_TIME_REQUIRED");
        }
    }

    public static DurableWorkflowWaitState none() {
        return new DurableWorkflowWaitState(
                DurableWorkflowWaitType.NONE, "", "", null, null, null, "");
    }

    public boolean active() {
        return type != DurableWorkflowWaitType.NONE && resumedAt == null;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
