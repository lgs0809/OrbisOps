package cn.lgs.orbisops.domain.agentdefinition.rule;

public record ExistsRule(WorkflowRuleField field) implements WorkflowRule {

    public ExistsRule {
        if (field == null) throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_REQUIRED");
    }
}
