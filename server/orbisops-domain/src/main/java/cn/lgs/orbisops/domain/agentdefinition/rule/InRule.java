package cn.lgs.orbisops.domain.agentdefinition.rule;

import java.util.ArrayList;
import java.util.List;

public record InRule(
        WorkflowRuleField field,
        List<Object> candidates
) implements WorkflowRule {

    public InRule {
        if (field == null) throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_REQUIRED");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("WORKFLOW_RULE_IN_VALUES_REQUIRED");
        }
        List<Object> normalized = new ArrayList<>(candidates.size());
        for (Object candidate : candidates) {
            normalized.add(WorkflowRuleValuePolicy.normalize(candidate));
        }
        candidates = List.copyOf(normalized);
    }
}
