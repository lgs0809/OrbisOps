package cn.lgs.orbisops.trigger.application.agentdefinition;

/** Combined Agent Definition boundary used only by consumers requiring queries and mutations. */
public interface OpsAgentDefinitionGateway extends
        OpsAgentDefinitionQueryGateway,
        OpsAgentDefinitionCommandGateway {
}
