package cn.lgs.orbisops.application.agentdefinition;

/** Outbound snapshot boundary needed before destructive Agent Definition lifecycle operations. */
public interface AgentDefinitionLifecycleOperationPort<S> {

    S currentSnapshot(String agentId);
}
