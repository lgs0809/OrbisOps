package cn.lgs.orbisops.domain.worksession.runtime.model;

import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable identity and capability binding frozen for a long-running Agent run. */
public record AgentSnapshot(
        String agentId,
        Integer agentVersion,
        String definitionHash,
        String systemPromptHash,
        String modelProfile,
        Set<String> toolBindingSnapshot,
        Set<String> mcpBindingSnapshot,
        Set<String> skillBindingSnapshot,
        Set<String> knowledgeBindingSnapshot) {

    public AgentSnapshot {
        agentId = text(agentId);
        agentVersion = agentVersion == null ? 0 : agentVersion;
        definitionHash = text(definitionHash);
        systemPromptHash = text(systemPromptHash);
        modelProfile = text(modelProfile);
        toolBindingSnapshot = copy(toolBindingSnapshot);
        mcpBindingSnapshot = copy(mcpBindingSnapshot);
        skillBindingSnapshot = copy(skillBindingSnapshot);
        knowledgeBindingSnapshot = copy(knowledgeBindingSnapshot);
    }

    private static Set<String> copy(Set<String> source) {
        if (source == null || source.isEmpty()) return Set.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        source.stream().map(AgentSnapshot::text).filter(value -> !value.isBlank()).forEach(values::add);
        return Set.copyOf(values);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
