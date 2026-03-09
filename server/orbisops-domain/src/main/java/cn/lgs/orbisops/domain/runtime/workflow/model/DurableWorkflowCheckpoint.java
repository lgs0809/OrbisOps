package cn.lgs.orbisops.domain.runtime.workflow.model;

import java.time.Instant;

public record DurableWorkflowCheckpoint(
        DurableWorkflowCheckpointType type,
        DurableWorkflowRunState state,
        String stateHash,
        Instant createdAt
) {

    public DurableWorkflowCheckpoint {
        if (type == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_CHECKPOINT_TYPE_REQUIRED");
        if (state == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_CHECKPOINT_STATE_REQUIRED");
        stateHash = stateHash == null ? "" : stateHash.trim();
        if (stateHash.isBlank()) throw new IllegalArgumentException("DURABLE_WORKFLOW_CHECKPOINT_HASH_REQUIRED");
        if (createdAt == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_CHECKPOINT_TIME_REQUIRED");
    }
}
