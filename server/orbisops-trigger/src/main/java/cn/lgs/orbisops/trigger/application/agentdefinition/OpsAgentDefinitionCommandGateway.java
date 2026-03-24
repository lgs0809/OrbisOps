package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

/** Stable mutation boundary for production consumers of Agent Definitions. */
public interface OpsAgentDefinitionCommandGateway {

    OpsAgentDefinition saveDraft(OpsAgentDefinition definition);

    OpsAgentDefinition validateVersion(String agentId, Integer version);

    OpsAgentDefinition publishVersion(String agentId, Integer version);

    OpsAgentDefinition rollback(String agentId, Integer version);

    boolean disableVersion(String agentId, Integer version);

    boolean delete(String agentId);

    void reload();
}
