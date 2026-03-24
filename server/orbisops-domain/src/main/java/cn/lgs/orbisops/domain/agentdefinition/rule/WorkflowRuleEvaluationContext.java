package cn.lgs.orbisops.domain.agentdefinition.rule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkflowRuleEvaluationContext(
        Map<String, Object> currentRoots,
        Map<String, Object> previousRoots
) {

    public WorkflowRuleEvaluationContext(Map<String, Object> currentRoots) {
        this(currentRoots, Map.of());
    }

    public WorkflowRuleEvaluationContext {
        currentRoots = roots(currentRoots);
        previousRoots = roots(previousRoots);
    }

    private static Map<String, Object> roots(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return Map.of();
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!WorkflowRuleField.allowedRoots().contains(key)) {
                throw new IllegalArgumentException("WORKFLOW_RULE_CONTEXT_ROOT_FORBIDDEN:" + key);
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
