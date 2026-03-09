package cn.lgs.orbisops.domain.agentdefinition.compilation;

import java.util.List;

public record WorkflowCompilationReport(
        boolean successful,
        List<String> completedStages,
        List<WorkflowCompilationFailure> failures
) {

    public WorkflowCompilationReport {
        completedStages = completedStages == null ? List.of() : List.copyOf(completedStages);
        failures = failures == null ? List.of() : List.copyOf(failures);
        if (successful && !failures.isEmpty()) {
            throw new IllegalArgumentException("WORKFLOW_COMPILATION_SUCCESS_WITH_FAILURES");
        }
        if (!successful && failures.isEmpty()) {
            throw new IllegalArgumentException("WORKFLOW_COMPILATION_FAILURE_REQUIRED");
        }
    }

    public static WorkflowCompilationReport success(List<String> stages) {
        return new WorkflowCompilationReport(true, stages, List.of());
    }

    public static WorkflowCompilationReport failure(
            List<String> stages,
            WorkflowCompilationFailure failure) {
        return new WorkflowCompilationReport(false, stages, List.of(failure));
    }
}
