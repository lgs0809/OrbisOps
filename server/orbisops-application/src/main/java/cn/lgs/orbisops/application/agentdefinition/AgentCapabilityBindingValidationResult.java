package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityReferenceSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Typed application result for Agent capability authorization validation. */
public record AgentCapabilityBindingValidationResult(
        String projectId,
        List<String> errors,
        List<String> warnings,
        AgentCapabilityReferenceSet references) {

    public AgentCapabilityBindingValidationResult {
        projectId = projectId == null ? "" : projectId.trim();
        errors = immutable(errors);
        warnings = immutable(warnings);
        references = references == null
                ? new AgentCapabilityReferenceSet(null, null, null, null, null)
                : references;
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public void requireValid() {
        if (!valid()) {
            throw new IllegalArgumentException(errors.isEmpty()
                    ? "Agent 能力绑定未通过项目授权校验"
                    : String.join("；", errors));
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
