package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCloneAuditPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionClonePort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.util.StringUtils;

/** Trigger adapter for mutable Agent Definition clone preparation and audit projection. */
public final class OpsAgentDefinitionCloneAdapter
        implements AgentDefinitionClonePort<OpsAgentDefinition>,
        AgentDefinitionCloneAuditPort<OpsAgentDefinition> {

    private final OpsAgentDefinitionGateway definitionGateway;
    private final OpsAgentCapabilityApplicationService capabilities;
    private final OpsAgentDefinitionExecutionShapeMapper executionShapeMapper;
    private final OpsConfigAuditService auditService;

    public OpsAgentDefinitionCloneAdapter(
            OpsAgentDefinitionGateway definitionGateway,
            OpsAgentCapabilityApplicationService capabilities,
            OpsAgentDefinitionExecutionShapeMapper executionShapeMapper,
            OpsConfigAuditService auditService) {
        if (definitionGateway == null
                || capabilities == null
                || auditService == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_CLONE_ADAPTER_DEPENDENCIES_REQUIRED");
        }
        this.definitionGateway = definitionGateway;
        this.capabilities = capabilities;
        this.executionShapeMapper = executionShapeMapper == null
                ? new OpsAgentDefinitionExecutionShapeMapper()
                : executionShapeMapper;
        this.auditService = auditService;
    }

    @Override
    public String requireExistingProject(String projectId) {
        return capabilities.requireExistingProject(projectId);
    }

    @Override
    public OpsAgentDefinition resolveSource(String sourceAgentId) {
        return definitionGateway.resolve(sourceAgentId, null, true);
    }

    @Override
    public OpsAgentDefinition sanitizeForProject(
            OpsAgentDefinition definition,
            String projectId) {
        capabilities.sanitizeForProject(definition, projectId);
        return definition;
    }

    @Override
    public OpsAgentDefinition prepareClone(
            OpsAgentDefinition definition,
            String targetProjectId,
            String newAgentId,
            String newName) {
        if (definition == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_CLONE_SOURCE_REQUIRED");
        }
        definition.setAgentId(newAgentId);
        definition.setProjectId(targetProjectId);
        definition.setName(StringUtils.hasText(newName)
                ? newName.trim()
                : value(definition.getName(), newAgentId) + " 副本");
        definition.setVersion(null);
        definition.setLifecycle("DRAFT");
        definition.setSource("UI");
        return definition;
    }

    @Override
    public OpsAgentDefinition normalizeExecutionShape(OpsAgentDefinition definition) {
        return executionShapeMapper.normalize(definition);
    }

    @Override
    public void recordClone(
            String sourceAgentId,
            String targetAgentId,
            OpsAgentDefinition clonedDefinition) {
        auditService.record(
                "agent-definition",
                "clone",
                targetAgentId,
                null,
                clonedDefinition);
    }

    private String value(String source, String fallback) {
        return StringUtils.hasText(source) ? source.trim() : fallback;
    }
}
