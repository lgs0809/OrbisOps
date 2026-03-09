package cn.lgs.orbisops.domain.agentdefinition.compilation;

public final class WorkflowCompilationException extends IllegalArgumentException {

    private final WorkflowCompilationReport report;

    public WorkflowCompilationException(WorkflowCompilationReport report, Throwable cause) {
        super(message(report), cause);
        if (report == null || report.successful()) {
            throw new IllegalArgumentException("WORKFLOW_COMPILATION_FAILURE_REPORT_REQUIRED");
        }
        this.report = report;
    }

    public WorkflowCompilationReport report() {
        return report;
    }

    private static String message(WorkflowCompilationReport report) {
        if (report == null || report.failures().isEmpty()) {
            return "WORKFLOW_COMPILATION_FAILED";
        }
        WorkflowCompilationFailure failure = report.failures().get(0);
        return failure.code() + ":" + failure.stageId() + ":" + failure.message();
    }
}
