package cn.lgs.orbisops.domain.agentdefinition.compilation;

public record AgentWorkflowCompilationResult(
        CompiledAgentDefinitionVersion output,
        WorkflowCompilationReport report
) {

    public AgentWorkflowCompilationResult {
        if (output == null) throw new IllegalArgumentException("WORKFLOW_COMPILATION_OUTPUT_REQUIRED");
        if (report == null || !report.successful()) {
            throw new IllegalArgumentException("WORKFLOW_COMPILATION_SUCCESS_REPORT_REQUIRED");
        }
    }
}
