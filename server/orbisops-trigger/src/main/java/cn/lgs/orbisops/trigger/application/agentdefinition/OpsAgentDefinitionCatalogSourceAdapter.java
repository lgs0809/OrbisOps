package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCatalogLoadModelPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCatalogSourcePort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSourceLoader;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/** Trigger adapter for YAML/JDBC Agent Definition catalog sources. */
@Slf4j
public final class OpsAgentDefinitionCatalogSourceAdapter implements
        AgentDefinitionCatalogSourcePort<OpsAgentDefinition>,
        AgentDefinitionCatalogLoadModelPort<OpsAgentDefinition> {

    private final AgentDefinitionSourceLoader<OpsAgentDefinition> yamlLoader;
    private final IAgentDefinitionRepository definitionRepository;
    private final AgentDefinitionSnapshotMapper<OpsAgentDefinition> snapshotMapper;

    public OpsAgentDefinitionCatalogSourceAdapter(
            AgentDefinitionSourceLoader<OpsAgentDefinition> yamlLoader,
            IAgentDefinitionRepository definitionRepository,
            AgentDefinitionSnapshotMapper<OpsAgentDefinition> snapshotMapper) {
        if (yamlLoader == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_YAML_LOADER_REQUIRED");
        }
        if (snapshotMapper == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_SNAPSHOT_MAPPER_REQUIRED");
        }
        this.yamlLoader = yamlLoader;
        this.definitionRepository = definitionRepository;
        this.snapshotMapper = snapshotMapper;
    }

    @Override
    public List<OpsAgentDefinition> loadPlatformDefinitions(String locations) {
        return safe(yamlLoader.load(locations));
    }

    @Override
    public List<OpsAgentDefinition> loadStoredCurrentDefinitions() {
        if (!storeAvailable()) {
            return List.of();
        }
        try {
            return definitionRepository.listCurrentEnabled().stream()
                    .map(snapshotMapper::fromSnapshot)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (Exception error) {
            log.warn("解析 MySQL 当前运维 Agent 定义失败，保留 YAML/fallback 定义", error);
            return List.of();
        }
    }

    @Override
    public List<OpsAgentDefinition> loadStoredVersionDefinitions() {
        if (!storeAvailable()) {
            return List.of();
        }
        try {
            return definitionRepository.listVersionsEnabled().stream()
                    .map(snapshotMapper::fromSnapshot)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (Exception error) {
            log.warn("解析 MySQL 运维 Agent 历史版本失败，保留已加载定义", error);
            return List.of();
        }
    }

    @Override
    public String agentId(OpsAgentDefinition definition) {
        return definition == null ? null : definition.getAgentId();
    }

    @Override
    public Integer version(OpsAgentDefinition definition) {
        return definition == null ? null : definition.getVersion();
    }

    @Override
    public String projectId(OpsAgentDefinition definition) {
        return definition == null ? null : definition.getProjectId();
    }

    @Override
    public String source(OpsAgentDefinition definition) {
        return definition == null ? null : definition.getSource();
    }

    private boolean storeAvailable() {
        return definitionRepository != null && definitionRepository.available();
    }

    private List<OpsAgentDefinition> safe(List<OpsAgentDefinition> definitions) {
        return definitions == null ? List.of() : definitions;
    }
}
