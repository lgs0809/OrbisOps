package cn.lgs.orbisops.domain.agentdefinition.compilation;

import java.util.ArrayList;
import java.util.List;

public final class CompiledPlanAssemblyStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "compiled-plan-assembly";

    public CompiledPlanAssemblyStage() {
        super(STAGE_ID, 800, WorkflowCompilationErrorCode.COMPILED_PLAN_INVALID);
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        List<String> completedStages = new ArrayList<>(context.completedStages());
        completedStages.add(STAGE_ID);
        context.setOutput(new CompiledAgentDefinitionVersion(
                context.definition().schemaVersion(),
                context.definition().definitionVersion(),
                context.definition().definitionHash(),
                context.definition().graph().agentId(),
                context.startNodeId(),
                context.compiledNodes(),
                context.compiledEdges(),
                context.reachableNodeIds(),
                context.topologicalOrder(),
                context.terminalNodeIds(),
                completedStages));
    }
}
