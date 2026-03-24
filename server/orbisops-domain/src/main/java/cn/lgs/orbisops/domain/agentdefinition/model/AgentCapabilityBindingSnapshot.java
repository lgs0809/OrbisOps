package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.List;
import java.util.Objects;

/** Complete capability binding set for one immutable Agent Definition version. */
public record AgentCapabilityBindingSnapshot(
        String agentId,
        int version,
        AgentDefinitionLifecycle lifecycle,
        String projectId,
        List<AgentCapabilityBinding> bindings) {

    public AgentCapabilityBindingSnapshot {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_AGENT_ID_REQUIRED");
        }
        agentId = agentId.trim();
        if (version < 0) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_VERSION_INVALID");
        }
        lifecycle = Objects.requireNonNull(lifecycle, "AGENT_CAPABILITY_LIFECYCLE_REQUIRED");
        projectId = projectId == null ? "" : projectId.trim();
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        for (AgentCapabilityBinding binding : bindings) {
            if (!agentId.equals(binding.agentId())
                    || version != binding.version()
                    || lifecycle != binding.lifecycle()
                    || !projectId.equals(binding.projectId())) {
                throw new IllegalArgumentException("AGENT_CAPABILITY_SNAPSHOT_BINDING_MISMATCH");
            }
        }
    }
}
