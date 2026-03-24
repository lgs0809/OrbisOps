package cn.lgs.orbisops.domain.agentdefinition.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;

import java.util.List;

/** Persistence boundary for typed Agent Definition capability bindings. */
public interface IAgentCapabilityBindingRepository {

    boolean available();

    List<AgentCapabilityBinding> findLatest(String agentId);

    void replace(AgentCapabilityBindingSnapshot snapshot);
}
