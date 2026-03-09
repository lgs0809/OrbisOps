package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.List;
import java.util.Map;

/** Stable query boundary for production consumers of Agent Definitions. */
public interface OpsAgentDefinitionQueryGateway {

    OpsAgentDefinition resolve(String agentDefinitionId);

    OpsAgentDefinition resolve(String agentDefinitionId,
                               Integer agentVersion,
                               boolean previewDraft);

    OpsAgentDefinition resolveForProject(String agentDefinitionId,
                                         Integer agentVersion,
                                         boolean previewDraft,
                                         String projectId);

    List<OpsAgentDefinition> list();

    List<OpsAgentDefinition> listForProject(String projectId);

    List<OpsAgentDefinition> listVersions(String agentId);

    List<Map<String, Object>> listCapabilityBindings(String agentId);

    AgentDefinitionVersionState describe(OpsAgentDefinition definition);
}
