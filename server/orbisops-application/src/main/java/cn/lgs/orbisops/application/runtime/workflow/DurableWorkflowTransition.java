package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowCheckpoint;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;

public record DurableWorkflowTransition(
        DurableWorkflowRunState state,
        DurableWorkflowCheckpoint checkpoint
) {

    public DurableWorkflowTransition {
        if (state == null || checkpoint == null) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_TRANSITION_REQUIRED");
        }
        if (state != checkpoint.state()) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_TRANSITION_STATE_MISMATCH");
        }
    }
}
