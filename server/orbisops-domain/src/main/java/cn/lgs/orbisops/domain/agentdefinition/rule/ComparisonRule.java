package cn.lgs.orbisops.domain.agentdefinition.rule;

public record ComparisonRule(
        WorkflowRuleField field,
        WorkflowComparisonOperator operator,
        Object expected
) implements WorkflowRule {

    public ComparisonRule {
        if (field == null) throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_REQUIRED");
        if (operator == null) throw new IllegalArgumentException("WORKFLOW_RULE_OPERATOR_REQUIRED");
        expected = WorkflowRuleValuePolicy.normalize(expected);
    }
}
