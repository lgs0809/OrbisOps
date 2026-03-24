package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionDraftSaveAuditPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionDraftSavePort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

/** Trigger adapter for mutable draft validation, normalization and audit projection. */
public final class OpsAgentDefinitionDraftSaveAdapter
        implements AgentDefinitionDraftSavePort<OpsAgentDefinition>,
        AgentDefinitionDraftSaveAuditPort<OpsAgentDefinition> {

    private final OpsAgentCapabilityApplicationService capabilities;
    private final OpsAgentDefinitionExecutionShapeMapper executionShapeMapper;
    private final OpsConfigAuditService auditService;

    public OpsAgentDefinitionDraftSaveAdapter(
            OpsAgentCapabilityApplicationService capabilities,
            OpsAgentDefinitionExecutionShapeMapper executionShapeMapper,
            OpsConfigAuditService auditService) {
        if (capabilities == null || auditService == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_DRAFT_SAVE_ADAPTER_DEPENDENCIES_REQUIRED");
        }
        this.capabilities = capabilities;
        this.executionShapeMapper = executionShapeMapper == null
                ? new OpsAgentDefinitionExecutionShapeMapper()
                : executionShapeMapper;
        this.auditService = auditService;
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
    public void recordDraftSave(
            String auditAction,
            OpsAgentDefinition savedDefinition) {
        auditService.record(
                "agent-definition",
                auditAction,
                savedDefinition.getAgentId(),
                null,
                savedDefinition);
    }
}
