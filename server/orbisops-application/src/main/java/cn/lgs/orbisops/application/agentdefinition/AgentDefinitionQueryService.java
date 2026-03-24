package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentDefinitionResolutionPolicy;

import java.util.List;

/** Application query service for immutable Agent Definition resolution. */
public final class AgentDefinitionQueryService<D> {

    private final AgentDefinitionCatalogPort<D> catalogPort;
    private final AgentDefinitionDescriptorPort<D> descriptorPort;
    private final ProjectAgentDirectoryPort projectDirectoryPort;
    private final AgentDefinitionResolutionPolicy resolutionPolicy;

    public AgentDefinitionQueryService(AgentDefinitionCatalogPort<D> catalogPort,
                                       AgentDefinitionDescriptorPort<D> descriptorPort,
                                       ProjectAgentDirectoryPort projectDirectoryPort) {
        this(catalogPort, descriptorPort, projectDirectoryPort, new AgentDefinitionResolutionPolicy());
    }

    AgentDefinitionQueryService(AgentDefinitionCatalogPort<D> catalogPort,
                                AgentDefinitionDescriptorPort<D> descriptorPort,
                                ProjectAgentDirectoryPort projectDirectoryPort,
                                AgentDefinitionResolutionPolicy resolutionPolicy) {
        if (catalogPort == null) throw new IllegalArgumentException("AGENT_DEFINITION_CATALOG_PORT_REQUIRED");
        if (descriptorPort == null) throw new IllegalArgumentException("AGENT_DEFINITION_DESCRIPTOR_PORT_REQUIRED");
        if (projectDirectoryPort == null) throw new IllegalArgumentException("PROJECT_AGENT_DIRECTORY_PORT_REQUIRED");
        if (resolutionPolicy == null) throw new IllegalArgumentException("AGENT_DEFINITION_RESOLUTION_POLICY_REQUIRED");
        this.catalogPort = catalogPort;
        this.descriptorPort = descriptorPort;
        this.projectDirectoryPort = projectDirectoryPort;
        this.resolutionPolicy = resolutionPolicy;
    }

    public D resolve(String requestedAgentId, Integer requestedVersion, boolean previewDraft) {
        boolean explicitAgentId = hasText(requestedAgentId);
        boolean explicitVersion = requestedVersion != null && requestedVersion > 0;
        String agentId = explicitAgentId ? requestedAgentId.trim() : requiredDefaultAgentId();
        D definition = explicitVersion
                ? catalogPort.findVersion(agentId, requestedVersion)
                : catalogPort.findCurrent(agentId);
        if (definition == null && !explicitAgentId) {
            definition = catalogPort.findCurrent(requiredDefaultAgentId());
        }
        if (definition == null) {
            String message = explicitVersion
                    ? "Agent 定义版本不存在或已下线："
                    : "Agent 定义不存在或已下线：";
            throw new IllegalArgumentException(message + agentId
                    + (explicitVersion ? "@" + requestedVersion : ""));
        }
        AgentDefinitionVersionState state = descriptorPort.describe(definition);
        resolutionPolicy.assertVisible(state, explicitVersion, previewDraft);
        return descriptorPort.snapshot(definition);
    }

    public D resolveForProject(String requestedAgentId,
                               Integer requestedVersion,
                               boolean previewDraft,
                               String projectId) {
        String normalizedProjectId = required(projectId, "运行 Agent 前必须选择 projectId");
        if (projectDirectoryPort.available() && !projectDirectoryPort.exists(normalizedProjectId)) {
            throw new IllegalArgumentException("业务系统不存在：" + normalizedProjectId);
        }
        String projectDefaultAgentId = projectDirectoryPort.available()
                ? projectDirectoryPort.defaultAgentId(normalizedProjectId)
                : null;
        String agentId = hasText(requestedAgentId)
                ? requestedAgentId.trim()
                : hasText(projectDefaultAgentId)
                ? projectDefaultAgentId.trim()
                : requiredDefaultAgentId();
        D definition = resolve(agentId, requestedVersion, previewDraft);
        resolutionPolicy.assertRunnableInProject(descriptorPort.describe(definition), normalizedProjectId);
        return definition;
    }

    public List<D> list() {
        return safe(catalogPort.findCurrentDefinitions()).stream()
                .map(descriptorPort::snapshot)
                .toList();
    }

    public List<D> listForProject(String projectId) {
        if (!hasText(projectId)) {
            return List.of();
        }
        String normalizedProjectId = projectId.trim();
        return safe(catalogPort.findCurrentDefinitions()).stream()
                .filter(definition -> resolutionPolicy.belongsToProject(
                        descriptorPort.describe(definition), normalizedProjectId))
                .map(descriptorPort::snapshot)
                .toList();
    }

    public List<D> versions(String agentId) {
        if (!hasText(agentId)) {
            return List.of();
        }
        return safe(catalogPort.findVersions(agentId.trim())).stream()
                .map(descriptorPort::snapshot)
                .toList();
    }

    private String requiredDefaultAgentId() {
        return required(catalogPort.defaultAgentId(), "AGENT_DEFAULT_DEFINITION_REQUIRED");
    }

    private List<D> safe(List<D> definitions) {
        return definitions == null ? List.of() : definitions;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String required(String value, String error) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(error);
        }
        return value.trim();
    }
}
