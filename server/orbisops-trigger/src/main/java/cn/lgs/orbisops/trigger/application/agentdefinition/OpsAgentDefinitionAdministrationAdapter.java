package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionAdministrationAuditPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionAdministrationPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Trigger adapter for Agent Definition delete/reload persistence and audit projection. */
public final class OpsAgentDefinitionAdministrationAdapter
        implements AgentDefinitionAdministrationPort<
        OpsAgentDefinition,
        Map<String, Object>>,
        AgentDefinitionAdministrationAuditPort<Map<String, Object>> {

    private final OpsAgentDefinitionGateway definitionGateway;
    private final OpsAgentDefinitionViewMapper viewMapper;
    private final OpsConfigAuditService auditService;

    public OpsAgentDefinitionAdministrationAdapter(
            OpsAgentDefinitionGateway definitionGateway,
            OpsAgentDefinitionViewMapper viewMapper,
            OpsConfigAuditService auditService) {
        if (definitionGateway == null || viewMapper == null || auditService == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_ADMINISTRATION_ADAPTER_DEPENDENCIES_REQUIRED");
        }
        this.definitionGateway = definitionGateway;
        this.viewMapper = viewMapper;
        this.auditService = auditService;
    }

    @Override
    public Map<String, Object> currentSnapshot(String agentId) {
        return definitionGateway.list().stream()
                .filter(definition -> definition != null
                        && definition.getAgentId() != null
                        && definition.getAgentId().equals(agentId))
                .findFirst()
                .map(viewMapper::view)
                .orElse(null);
    }

    @Override
    public boolean delete(String agentId) {
        return definitionGateway.delete(agentId);
    }

    @Override
    public void reload() {
        definitionGateway.reload();
    }

    @Override
    public List<OpsAgentDefinition> listProjectDefinitions() {
        return definitionGateway.list().stream()
                .filter(definition -> definition != null
                        && StringUtils.hasText(definition.getProjectId()))
                .toList();
    }

    @Override
    public void recordDelete(
            String agentId,
            Map<String, Object> beforeSnapshot,
            boolean deleted) {
        auditService.record(
                "agent-definition",
                "delete",
                agentId,
                beforeSnapshot,
                Map.of("deleted", deleted));
    }

    @Override
    public void recordReload() {
        auditService.record(
                "agent-definition",
                "reload",
                "all",
                null,
                Map.of("reloaded", true));
    }
}
