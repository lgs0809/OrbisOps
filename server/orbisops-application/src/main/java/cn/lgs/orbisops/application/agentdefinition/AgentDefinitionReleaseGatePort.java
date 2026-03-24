package cn.lgs.orbisops.application.agentdefinition;

public interface AgentDefinitionReleaseGatePort<D> {
    void assertReleaseAllowed(D definition);
}
