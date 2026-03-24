package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;

import java.util.List;

/** Application boundary for replacing and querying Agent Definition capability bindings. */
public final class AgentCapabilityBindingUseCase {

    private final IAgentCapabilityBindingRepository repository;

    public AgentCapabilityBindingUseCase(IAgentCapabilityBindingRepository repository) {
        this.repository = repository;
    }

    public boolean available() {
        return repository != null && repository.available();
    }

    public List<AgentCapabilityBinding> latest(String agentId) {
        if (agentId == null || agentId.isBlank() || !available()) {
            return List.of();
        }
        return repository.findLatest(agentId.trim());
    }

    public void replace(AgentCapabilityBindingSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_BINDING_SNAPSHOT_REQUIRED");
        }
        if (!available()) {
            throw new IllegalStateException("AGENT_CAPABILITY_BINDING_STORE_UNAVAILABLE");
        }
        repository.replace(snapshot);
    }
}
