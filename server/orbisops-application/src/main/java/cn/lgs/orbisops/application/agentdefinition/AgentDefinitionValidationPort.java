package cn.lgs.orbisops.application.agentdefinition;

public interface AgentDefinitionValidationPort<D> {
    void validate(D definition);
}
