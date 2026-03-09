package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentDefinitionFacts;
import cn.lgs.orbisops.application.agentdefinition.ProjectDefaultAgentDefinitionPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Trigger adapter for project default Agent definition queries and mutable DTO preparation. */
public final class OpsProjectDefaultAgentDefinitionAdapter
        implements ProjectDefaultAgentDefinitionPort<OpsAgentDefinition> {

    private final OpsAgentDefinitionGateway definitionGateway;
    private final OpsAgentCapabilityApplicationService capabilityApplicationService;
    private final OpsAgentDefinitionExecutionShapeMapper executionShapeMapper;

    public OpsProjectDefaultAgentDefinitionAdapter(
            OpsAgentDefinitionGateway definitionGateway,
            OpsAgentCapabilityApplicationService capabilityApplicationService,
            OpsAgentDefinitionExecutionShapeMapper executionShapeMapper) {
        if (definitionGateway == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_GATEWAY_REQUIRED");
        }
        if (capabilityApplicationService == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_CAPABILITY_SERVICE_REQUIRED");
        }
        this.definitionGateway = definitionGateway;
        this.capabilityApplicationService = capabilityApplicationService;
        this.executionShapeMapper = executionShapeMapper == null
                ? new OpsAgentDefinitionExecutionShapeMapper()
                : executionShapeMapper;
    }

    @Override
    public String requireExistingProject(String projectId) {
        return capabilityApplicationService.requireExistingProject(projectId);
    }

    @Override
    public List<OpsAgentDefinition> versions(String agentId) {
        return definitionGateway.listVersions(agentId);
    }

    @Override
    public OpsAgentDefinition loadDefaultTemplate() {
        return definitionGateway.resolve(
                OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID,
                null,
                true);
    }

    @Override
    public OpsAgentDefinition sanitizeForProject(
            OpsAgentDefinition definition,
            String projectId) {
        capabilityApplicationService.sanitizeForProject(definition, projectId);
        return definition;
    }

    @Override
    public OpsAgentDefinition prepareDraft(
            OpsAgentDefinition definition,
            String projectId,
            String agentId,
            String agentName) {
        if (definition == null) {
            throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_TEMPLATE_REQUIRED");
        }
        definition.setAgentId(agentId);
        definition.setProjectId(projectId);
        definition.setName(agentName);
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
    public ProjectDefaultAgentDefinitionFacts facts(OpsAgentDefinition definition) {
        if (definition == null) {
            return new ProjectDefaultAgentDefinitionFacts(
                    "", "", null, "", "", Set.of());
        }
        Set<String> roles = Optional.ofNullable(definition.getAgentscopeAgents())
                .orElse(List.of())
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(OpsAgentScopeConfig::getRole)
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        return new ProjectDefaultAgentDefinitionFacts(
                definition.getAgentId(),
                definition.getProjectId(),
                definition.getVersion(),
                definition.getLifecycle(),
                definition.getDefinitionHash(),
                roles);
    }
}
