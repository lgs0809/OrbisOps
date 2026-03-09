package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;

public final class SchemaValidationStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "schema-validation";

    public SchemaValidationStage() {
        super(STAGE_ID, 100, WorkflowCompilationErrorCode.SCHEMA_INVALID);
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        AgentWorkflowDefinition definition = context.definition();
        if (definition.schemaVersion() != AgentWorkflowDefinition.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "WORKFLOW_SCHEMA_VERSION_UNSUPPORTED:" + definition.schemaVersion());
        }
        if (definition.graph().agentId().isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_AGENT_ID_REQUIRED");
        }
        if (definition.graph().nodes().isEmpty()) {
            throw new IllegalArgumentException("WORKFLOW_NODES_REQUIRED");
        }
    }
}
