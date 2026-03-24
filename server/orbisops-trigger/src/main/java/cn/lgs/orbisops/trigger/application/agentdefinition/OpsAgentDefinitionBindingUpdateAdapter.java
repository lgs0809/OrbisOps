package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionBindingUpdateAuditPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionBindingUpdatePort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.List;
import java.util.Map;

/** Trigger adapter for mutable capability binding updates and compatibility audit projection. */
public final class OpsAgentDefinitionBindingUpdateAdapter
        implements AgentDefinitionBindingUpdatePort<
        OpsAgentDefinition,
        Map<String, Object>,
        List<Map<String, Object>>>,
        AgentDefinitionBindingUpdateAuditPort<
                OpsAgentDefinition,
                List<Map<String, Object>>> {

    private final OpsAgentDefinitionGateway definitionGateway;
    private final OpsAgentCapabilityApplicationService capabilities;
    private final OpsAgentDefinitionExecutionShapeMapper executionShapeMapper;
    private final OpsConfigAuditService auditService;

    public OpsAgentDefinitionBindingUpdateAdapter(
            OpsAgentDefinitionGateway definitionGateway,
            OpsAgentCapabilityApplicationService capabilities,
            OpsAgentDefinitionExecutionShapeMapper executionShapeMapper,
            OpsConfigAuditService auditService) {
        if (definitionGateway == null || capabilities == null || auditService == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_BINDING_UPDATE_ADAPTER_DEPENDENCIES_REQUIRED");
        }
        this.definitionGateway = definitionGateway;
        this.capabilities = capabilities;
        this.executionShapeMapper = executionShapeMapper == null
                ? new OpsAgentDefinitionExecutionShapeMapper()
                : executionShapeMapper;
        this.auditService = auditService;
    }

    @Override
    public OpsAgentDefinition resolveDraft(String agentId) {
        return definitionGateway.resolve(agentId, null, true);
    }

    @Override
    public OpsAgentDefinition applyBindings(
            OpsAgentDefinition definition,
            List<Map<String, Object>> bindings) {
        capabilities.applyBindings(definition, bindings);
        return definition;
    }

    @Override
    public void assertProjectAndBindingsValid(OpsAgentDefinition definition) {
        if (definition == null || definition.getProjectId() == null
                || definition.getProjectId().isBlank()) {
            throw new IllegalArgumentException(
                    "Agent 必须归属一个项目；跨项目复用请使用复制功能");
        }
        capabilities.requireExistingProject(definition.getProjectId());
        capabilities.assertValid(definition);
    }

    @Override
    public OpsAgentDefinition normalizeExecutionShape(OpsAgentDefinition definition) {
        return executionShapeMapper.normalize(definition);
    }

    @Override
    public List<Map<String, Object>> effectiveBindings(OpsAgentDefinition savedDefinition) {
        List<Map<String, Object>> stored =
                definitionGateway.listCapabilityBindings(savedDefinition.getAgentId());
        if (stored != null && !stored.isEmpty()) {
            return List.copyOf(stored);
        }
        return capabilities.extractBindings(savedDefinition);
    }

    @Override
    public void recordBindingUpdate(
            String agentId,
            OpsAgentDefinition savedDefinition,
            List<Map<String, Object>> effectiveBindings) {
        auditService.record(
                "agent-definition",
                "update-bindings",
                savedDefinition.getAgentId(),
                null,
                Map.of(
                        "agentId", savedDefinition.getAgentId(),
                        "projectId", savedDefinition.getProjectId(),
                        "bindings", effectiveBindings == null
                                ? List.of()
                                : List.copyOf(effectiveBindings)));
    }
}
