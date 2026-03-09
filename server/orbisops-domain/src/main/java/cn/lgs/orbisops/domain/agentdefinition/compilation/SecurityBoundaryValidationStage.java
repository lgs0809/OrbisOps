package cn.lgs.orbisops.domain.agentdefinition.compilation;

public final class SecurityBoundaryValidationStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "security-boundary-validation";

    public SecurityBoundaryValidationStage() {
        super(STAGE_ID, 700, WorkflowCompilationErrorCode.SECURITY_BOUNDARY_INVALID);
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        context.hooks().validateSecurityBoundary(context.definition());
    }
}
