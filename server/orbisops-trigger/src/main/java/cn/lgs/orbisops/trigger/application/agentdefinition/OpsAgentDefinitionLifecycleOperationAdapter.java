package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecycleOperationAuditPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecycleOperationPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.Map;

/** Trigger adapter for lifecycle before-snapshot and audit compatibility projection. */
public final class OpsAgentDefinitionLifecycleOperationAdapter
        implements AgentDefinitionLifecycleOperationPort<Map<String, Object>>,
        AgentDefinitionLifecycleOperationAuditPort<
                OpsAgentDefinition,
                Map<String, Object>> {

    private final OpsAgentDefinitionGateway definitionGateway;
    private final OpsAgentDefinitionViewMapper viewMapper;
    private final OpsConfigAuditService auditService;

    public OpsAgentDefinitionLifecycleOperationAdapter(
            OpsAgentDefinitionGateway definitionGateway,
            OpsAgentDefinitionViewMapper viewMapper,
            OpsConfigAuditService auditService) {
        if (definitionGateway == null || viewMapper == null || auditService == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_LIFECYCLE_OPERATION_ADAPTER_DEPENDENCIES_REQUIRED");
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
    public void recordTransition(
            String action,
            String agentId,
            int version,
            OpsAgentDefinition definition) {
        auditService.record(
                "agent-definition",
                action,
                agentId + ":" + version,
                null,
                definition);
    }

    @Override
    public void recordDisable(
            String agentId,
            int version,
            Map<String, Object> beforeSnapshot,
            boolean disabled) {
        auditService.record(
                "agent-definition",
                "disable",
                agentId + ":" + version,
                beforeSnapshot,
                Map.of("disabled", disabled));
    }
}
