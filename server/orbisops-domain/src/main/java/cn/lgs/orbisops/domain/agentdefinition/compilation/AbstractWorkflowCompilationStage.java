package cn.lgs.orbisops.domain.agentdefinition.compilation;

abstract class AbstractWorkflowCompilationStage implements AgentWorkflowCompilationStage {

    private final String stageId;
    private final int order;
    private final WorkflowCompilationErrorCode failureCode;

    protected AbstractWorkflowCompilationStage(
            String stageId,
            int order,
            WorkflowCompilationErrorCode failureCode) {
        this.stageId = required(stageId, "WORKFLOW_COMPILATION_STAGE_ID_REQUIRED");
        this.order = order;
        if (failureCode == null) {
            throw new IllegalArgumentException("WORKFLOW_COMPILATION_STAGE_ERROR_CODE_REQUIRED");
        }
        this.failureCode = failureCode;
    }

    @Override
    public final String stageId() {
        return stageId;
    }

    @Override
    public final int order() {
        return order;
    }

    @Override
    public final WorkflowCompilationErrorCode failureCode() {
        return failureCode;
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
