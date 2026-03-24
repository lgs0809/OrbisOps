package cn.lgs.orbisops.domain.agentdefinition.compilation;

public final class CapabilityReferenceValidationStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "capability-reference-validation";

    public CapabilityReferenceValidationStage() {
        super(STAGE_ID, 600, WorkflowCompilationErrorCode.CAPABILITY_REFERENCE_INVALID);
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        context.hooks().validateCapabilities(context.definition());
    }
}
