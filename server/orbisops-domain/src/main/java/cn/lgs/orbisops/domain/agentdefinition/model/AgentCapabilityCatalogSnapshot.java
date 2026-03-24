package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Authorized project capability catalog used to sanitize one Agent Definition. */
public record AgentCapabilityCatalogSnapshot(
        List<String> knowledgeBaseIds,
        Set<String> skillIds,
        Set<String> projectToolIds,
        Set<String> executionTargetIds) {

    public AgentCapabilityCatalogSnapshot {
        knowledgeBaseIds = knowledgeBaseIds == null
                ? List.of()
                : knowledgeBaseIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        skillIds = immutable(skillIds);
        projectToolIds = immutable(projectToolIds);
        executionTargetIds = immutable(executionTargetIds);
    }

    public String primaryKnowledgeBaseId() {
        return knowledgeBaseIds.isEmpty() ? "" : knowledgeBaseIds.get(0);
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
