package cn.lgs.orbisops.domain.agentdefinition.rule;

public record ChangedRule(WorkflowRuleField field) implements WorkflowRule {

    public ChangedRule {
        if (field == null) throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_REQUIRED");
    }
}
