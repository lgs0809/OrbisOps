package cn.lgs.orbisops.domain.agentdefinition.model;

/** Raw rule expression retained until the dedicated Rule AST phase. */
public record AgentWorkflowRuleExpression(
        AgentWorkflowRouteMode mode,
        String expression
) {

    public AgentWorkflowRuleExpression {
        if (mode == null) throw new IllegalArgumentException("WORKFLOW_RULE_MODE_REQUIRED");
        expression = expression == null ? "" : expression.trim();
    }

    public boolean conditional() {
        return mode.conditional();
    }
}
