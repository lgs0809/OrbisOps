package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;

public final class NodeDefinitionCompilationStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "node-definition-compilation";

    private final AgentWorkflowNodeCompilerRegistry compilers;

    public NodeDefinitionCompilationStage(AgentWorkflowNodeCompilerRegistry compilers) {
        super(STAGE_ID, 200, WorkflowCompilationErrorCode.NODE_CONFIG_INVALID);
        if (compilers == null) throw new IllegalArgumentException("WORKFLOW_NODE_COMPILER_REGISTRY_REQUIRED");
        this.compilers = compilers;
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        for (AgentGraphDefinition.Node rawNode : context.definition().graph().nodes()) {
            AgentWorkflowNodeDefinition node;
            try {
                node = rawNode.workflowDefinition();
            } catch (IllegalArgumentException error) {
                if (error.getMessage() != null
                        && error.getMessage().startsWith("WORKFLOW_NODE_TYPE_UNKNOWN:")) {
                    throw typedFailure(
                            WorkflowCompilationErrorCode.NODE_TYPE_INVALID,
                            rawNode.nodeId(),
                            error,
                            context);
                }
                throw error;
            }
            try {
                context.addCompiledNode(compilers.compile(node, context));
            } catch (IllegalStateException error) {
                if (error.getMessage() != null
                        && error.getMessage().startsWith("WORKFLOW_NODE_COMPILER_NOT_FOUND:")) {
                    throw typedFailure(
                            WorkflowCompilationErrorCode.NODE_COMPILER_NOT_FOUND,
                            node.nodeId(),
                            error,
                            context);
                }
                throw error;
            }
        }
    }

    private WorkflowCompilationException typedFailure(
            WorkflowCompilationErrorCode code,
            String subjectId,
            RuntimeException error,
            AgentWorkflowCompilationContext context) {
        WorkflowCompilationFailure failure = new WorkflowCompilationFailure(
                code,
                STAGE_ID,
                subjectId,
                error.getMessage());
        return new WorkflowCompilationException(
                WorkflowCompilationReport.failure(context.completedStages(), failure),
                error);
    }
}
