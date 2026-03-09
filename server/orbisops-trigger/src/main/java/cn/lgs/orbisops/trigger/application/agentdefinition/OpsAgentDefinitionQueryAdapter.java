package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionQueryPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Trigger adapter for Agent Definition and compatibility binding queries. */
public final class OpsAgentDefinitionQueryAdapter
        implements AgentDefinitionQueryPort<
        OpsAgentDefinition,
        Map<String, Object>> {

    private final OpsAgentDefinitionGateway definitionGateway;
    private final OpsAgentCapabilityApplicationService capabilities;

    public OpsAgentDefinitionQueryAdapter(
            OpsAgentDefinitionGateway definitionGateway,
            OpsAgentCapabilityApplicationService capabilities) {
        if (definitionGateway == null || capabilities == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_QUERY_ADAPTER_DEPENDENCIES_REQUIRED");
        }
        this.definitionGateway = definitionGateway;
        this.capabilities = capabilities;
    }

    @Override
    public List<OpsAgentDefinition> listAll() {
        return definitionGateway.list();
    }

    @Override
    public List<OpsAgentDefinition> listForProject(String projectId) {
        return definitionGateway.listForProject(projectId);
    }

    @Override
    public List<OpsAgentDefinition> listVersions(String agentId) {
        return definitionGateway.listVersions(agentId);
    }

    @Override
    public OpsAgentDefinition resolveDraft(String agentId) {
        return definitionGateway.resolve(agentId, null, true);
    }

    @Override
    public List<Map<String, Object>> storedBindings(String agentId) {
        return definitionGateway.listCapabilityBindings(agentId);
    }

    @Override
    public List<Map<String, Object>> deriveBindings(OpsAgentDefinition definition) {
        return capabilities.extractBindings(definition);
    }

    @Override
    public boolean projectScoped(OpsAgentDefinition definition) {
        return definition != null && StringUtils.hasText(definition.getProjectId());
    }

    @Override
    public String agentId(OpsAgentDefinition definition) {
        return definition == null || definition.getAgentId() == null
                ? ""
                : definition.getAgentId().trim();
    }
}
