package cn.lgs.orbisops.domain.agentdefinition.compilation;

public interface AgentWorkflowCompilationStage {

    String stageId();

    int order();

    WorkflowCompilationErrorCode failureCode();

    void compile(AgentWorkflowCompilationContext context);
}
