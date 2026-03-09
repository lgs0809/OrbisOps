package cn.lgs.orbisops.domain.agentdefinition.rule;

public record NotRule(WorkflowRule rule) implements WorkflowRule {

    public NotRule {
        if (rule == null) throw new IllegalArgumentException("WORKFLOW_RULE_CHILD_REQUIRED");
    }
}
