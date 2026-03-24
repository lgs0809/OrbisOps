package cn.lgs.orbisops.domain.agentdefinition.compilation;

public final class ControlFlowValidationStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "control-flow-validation";

    private final AgentWorkflowControlFlowCompiler compiler;

    public ControlFlowValidationStage(AgentWorkflowControlFlowCompiler compiler) {
        super(STAGE_ID, 500, WorkflowCompilationErrorCode.CONTROL_FLOW_INVALID);
        if (compiler == null) {
            throw new IllegalArgumentException("WORKFLOW_CONTROL_FLOW_COMPILER_REQUIRED");
        }
        this.compiler = compiler;
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        AgentWorkflowControlFlowFacts facts = compiler.compile(
                context.definition(),
                context.compiledNodes(),
                context.compiledEdges());
        if (facts == null) {
            throw new IllegalStateException("WORKFLOW_CONTROL_FLOW_FACTS_REQUIRED");
        }
        context.setControlFlowFacts(
                facts.startNodeId(),
                facts.reachableNodeIds(),
                facts.topologicalOrder(),
                facts.terminalNodeIds());
    }
}
