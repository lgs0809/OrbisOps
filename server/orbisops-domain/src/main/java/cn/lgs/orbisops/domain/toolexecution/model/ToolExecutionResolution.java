package cn.lgs.orbisops.domain.toolexecution.model;

public record ToolExecutionResolution(
        ToolExecutionTarget target,
        ToolExecutionDecision decision) {

    public ToolExecutionResolution {
        if (target == null) throw new IllegalArgumentException("TOOL_EXECUTION_TARGET_REQUIRED");
        if (decision == null) throw new IllegalArgumentException("TOOL_EXECUTION_DECISION_REQUIRED");
    }
}
