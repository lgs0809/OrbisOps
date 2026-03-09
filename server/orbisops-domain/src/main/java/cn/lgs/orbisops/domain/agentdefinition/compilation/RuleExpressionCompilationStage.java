package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowEdgeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleCompiler;

import java.util.ArrayList;
import java.util.List;

public final class RuleExpressionCompilationStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "rule-expression-compilation";
    private static final int MAX_EXPRESSION_CHARS = 8192;

    private final WorkflowRuleCompiler ruleCompiler;

    public RuleExpressionCompilationStage(WorkflowRuleCompiler ruleCompiler) {
        super(STAGE_ID, 300, WorkflowCompilationErrorCode.RULE_INVALID);
        if (ruleCompiler == null) {
            throw new IllegalArgumentException("WORKFLOW_RULE_COMPILER_REQUIRED");
        }
        this.ruleCompiler = ruleCompiler;
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        List<CompiledWorkflowEdge> compiled = new ArrayList<>();
        for (AgentWorkflowEdgeDefinition edge : context.definition().graph().workflowEdges()) {
            String expression = edge.rule().expression();
            if (expression.length() > MAX_EXPRESSION_CHARS) {
                throw new IllegalArgumentException(
                        "WORKFLOW_RULE_EXPRESSION_TOO_LARGE:"
                                + edge.fromNodeId() + "->" + edge.toNodeId());
            }
            WorkflowRule rule = ruleCompiler.compile(edge.routeMode(), expression);
            compiled.add(new CompiledWorkflowEdge(
                    edge.edgeId(),
                    edge.fromNodeId(),
                    edge.toNodeId(),
                    edge.routeMode(),
                    edge.rule(),
                    rule,
                    edge.defaultEdge(),
                    edge.feedbackEdge(),
                    edge.priority(),
                    edge.dataMapping()));
        }
        context.setCompiledEdges(compiled);
    }
}
