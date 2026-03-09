package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityReferenceSet;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Extracts the authoritative capability-reference set from an Agent Definition snapshot. */
public final class AgentCapabilityBindingPolicy {

    public AgentCapabilityReferenceSet summarize(
            AgentCapabilityBindingSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "AGENT_CAPABILITY_BINDING_SNAPSHOT_REQUIRED");
        }
        Set<String> skills = refs(snapshot, AgentCapabilityType.SKILL);
        Set<String> projectTools = refs(snapshot, AgentCapabilityType.PROJECT_TOOL);
        Set<String> knowledgeBases = refs(snapshot, AgentCapabilityType.KNOWLEDGE_BASE);
        Set<String> executionTargets = refs(snapshot, AgentCapabilityType.EXECUTION_TARGET);
        List<String> inlineOwners = snapshot.bindings().stream()
                .filter(java.util.Objects::nonNull)
                .filter(binding -> binding.capabilityType()
                        == AgentCapabilityType.INLINE_MCP_SERVER)
                .map(this::inlineOwner)
                .distinct()
                .toList();
        return new AgentCapabilityReferenceSet(
                skills,
                projectTools,
                knowledgeBases,
                executionTargets,
                inlineOwners);
    }

    private Set<String> refs(
            AgentCapabilityBindingSnapshot snapshot,
            AgentCapabilityType type) {
        return snapshot.bindings().stream()
                .filter(java.util.Objects::nonNull)
                .filter(binding -> binding.capabilityType() == type)
                .map(AgentCapabilityBinding::capabilityId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String inlineOwner(AgentCapabilityBinding binding) {
        AgentCapabilityOwnerType ownerType = binding.ownerType();
        return switch (ownerType) {
            case AGENT -> "AGENT:" + text(binding.agentId(), "root");
            case NODE -> "NODE:" + text(binding.nodeId(), "unnamed");
            case AGENTSCOPE -> "AGENTSCOPE:" + text(binding.nodeId(), "unnamed");
        };
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
