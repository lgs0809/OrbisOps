package cn.lgs.orbisops.domain.agentdefinition.rule;

import java.util.List;

public record AnyRule(List<WorkflowRule> rules) implements WorkflowRule {

    public AnyRule {
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (rules.stream().anyMatch(rule -> rule == null)) {
            throw new IllegalArgumentException("WORKFLOW_RULE_CHILD_REQUIRED");
        }
    }
}
