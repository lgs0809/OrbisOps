package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentCapabilityBindingUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMutationModelPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMutationStorePort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionGraphRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinitionValidator;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Trigger adapter for Agent Definition mutation model and persistence projections. */
public final class OpsAgentDefinitionMutationAdapter implements
        AgentDefinitionMutationModelPort<OpsAgentDefinition>,
        AgentDefinitionMutationStorePort<OpsAgentDefinition> {

    private final OpsAgentDefinitionValidator definitionValidator;
    private final AgentDefinitionSnapshotMapper<OpsAgentDefinition> snapshotMapper;
    private final IAgentDefinitionRepository definitionRepository;
    private final IAgentDefinitionGraphRepository graphRepository;
    private final AgentCapabilityBindingUseCase capabilityBindingUseCase;
    private final OpsAgentDefinitionNormalizedGraphMapper normalizedGraphMapper;
    private final OpsAgentCapabilityBindingMapper capabilityBindingMapper;
    private final AgentWorkflowDefinitionMigrator workflowMigrator;

    public OpsAgentDefinitionMutationAdapter(
            OpsAgentDefinitionValidator definitionValidator,
            AgentDefinitionSnapshotMapper<OpsAgentDefinition> snapshotMapper,
            IAgentDefinitionRepository definitionRepository,
            IAgentDefinitionGraphRepository graphRepository,
            IAgentCapabilityBindingRepository capabilityRepository) {
        if (definitionValidator == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_VALIDATOR_REQUIRED");
        }
        if (snapshotMapper == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_SNAPSHOT_MAPPER_REQUIRED");
        }
        this.definitionValidator = definitionValidator;
        this.snapshotMapper = snapshotMapper;
        this.definitionRepository = definitionRepository;
        this.graphRepository = graphRepository;
        this.capabilityBindingUseCase = new AgentCapabilityBindingUseCase(capabilityRepository);
        this.normalizedGraphMapper = new OpsAgentDefinitionNormalizedGraphMapper();
        this.capabilityBindingMapper = new OpsAgentCapabilityBindingMapper();
        this.workflowMigrator = new AgentWorkflowDefinitionMigrator();
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
    public String definitionHash(OpsAgentDefinition definition) {
        return definition == null ? null : definition.getDefinitionHash();
    }

    @Override
    public boolean hasLifecycle(OpsAgentDefinition definition) {
        return definition != null && StringUtils.hasText(definition.getLifecycle());
    }

    @Override
    public void assignVersion(OpsAgentDefinition definition, int version) {
        definition.setVersion(version);
    }

    @Override
    public void assignLifecycle(OpsAgentDefinition definition,
                                AgentDefinitionLifecycle lifecycle) {
        definition.setLifecycle(lifecycle.name());
    }

    @Override
    public void assignSource(OpsAgentDefinition definition, String source) {
        definition.setSource(source);
    }

    @Override
    public void assignDefinitionHash(OpsAgentDefinition definition,
                                     String definitionHash) {
        definition.setDefinitionHash(definitionHash);
    }

    @Override
    public void normalize(OpsAgentDefinition definition) {
        workflowMigrator.migrate(definition);
    }

    @Override
    public void validate(OpsAgentDefinition definition) {
        definitionValidator.validate(definition);
    }

    @Override
    public OpsAgentDefinition snapshot(OpsAgentDefinition definition) {
        return snapshotMapper.copy(definition);
    }

    @Override
    public String calculateHash(OpsAgentDefinition definition) {
        return snapshotMapper.definitionHash(definition);
    }

    @Override
    public boolean available() {
        return definitionRepository != null && definitionRepository.available();
    }

    @Override
    public int maxVersion(String agentId) {
        return available() ? definitionRepository.maxVersion(agentId) : 0;
    }

    @Override
    public Optional<OpsAgentDefinition> findVersion(String agentId, int version) {
        return available()
                ? definitionRepository.findVersion(agentId, version)
                .flatMap(snapshotMapper::fromSnapshot)
                : Optional.empty();
    }

    @Override
    public void saveVersion(OpsAgentDefinition definition,
                            boolean currentPublished) {
        requireDefinitionStore();
        definitionRepository.saveVersion(
                snapshotMapper.toSnapshot(definition, currentPublished));
        if (currentPublished) {
            replaceGraph(definition);
        }
        replaceCapabilities(definition);
    }

    @Override
    public AgentDefinitionPublishResult publish(OpsAgentDefinition definition,
                                                String expectedVersionHash) {
        requireDefinitionStore();
        AgentDefinitionPublishResult result = definitionRepository.publish(
                snapshotMapper.toSnapshot(definition, true),
                expectedVersionHash);
        if (result == AgentDefinitionPublishResult.PUBLISHED) {
            replaceGraph(definition);
            replaceCapabilities(definition);
        }
        return result;
    }

    @Override
    public boolean disableVersion(String agentId, int version) {
        requireDefinitionStore();
        return definitionRepository.disableVersion(agentId, version);
    }

    @Override
    public void disableCurrent(String agentId) {
        requireDefinitionStore();
        definitionRepository.disableCurrent(agentId);
    }

    private void replaceGraph(OpsAgentDefinition definition) {
        if (graphRepository == null || !graphRepository.available()) {
            throw new IllegalStateException("AGENT_DEFINITION_GRAPH_STORE_UNAVAILABLE");
        }
        graphRepository.replace(normalizedGraphMapper.map(definition));
    }

    private void replaceCapabilities(OpsAgentDefinition definition) {
        if (!capabilityBindingUseCase.available()) {
            throw new IllegalStateException("AGENT_CAPABILITY_BINDING_STORE_UNAVAILABLE");
        }
        capabilityBindingUseCase.replace(capabilityBindingMapper.map(definition));
    }

    private void requireDefinitionStore() {
        if (!available()) {
            throw new IllegalStateException("AGENT_DEFINITION_STORE_UNAVAILABLE");
        }
    }

}
