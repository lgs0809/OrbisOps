package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Distinct capability references and forbidden inline owners extracted from one Agent Definition. */
public record AgentCapabilityReferenceSet(
        Set<String> skillRefs,
        Set<String> projectToolRefs,
        Set<String> knowledgeBaseRefs,
        Set<String> executionTargetRefs,
        List<String> inlineMcpOwners) {

    public AgentCapabilityReferenceSet {
        skillRefs = immutable(skillRefs);
        projectToolRefs = immutable(projectToolRefs);
        knowledgeBaseRefs = immutable(knowledgeBaseRefs);
        executionTargetRefs = immutable(executionTargetRefs);
        inlineMcpOwners = inlineMcpOwners == null
                ? List.of()
                : inlineMcpOwners.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
