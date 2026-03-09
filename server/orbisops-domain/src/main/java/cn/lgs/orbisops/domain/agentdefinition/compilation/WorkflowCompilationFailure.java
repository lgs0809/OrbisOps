package cn.lgs.orbisops.domain.agentdefinition.compilation;

/** Immutable typed compilation failure. */
public record WorkflowCompilationFailure(
        WorkflowCompilationErrorCode code,
        String stageId,
        String subjectId,
        String message
) {

    public WorkflowCompilationFailure {
        if (code == null) throw new IllegalArgumentException("WORKFLOW_COMPILATION_ERROR_CODE_REQUIRED");
        stageId = required(stageId, "WORKFLOW_COMPILATION_STAGE_REQUIRED");
        subjectId = text(subjectId);
        message = required(message, "WORKFLOW_COMPILATION_MESSAGE_REQUIRED");
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
