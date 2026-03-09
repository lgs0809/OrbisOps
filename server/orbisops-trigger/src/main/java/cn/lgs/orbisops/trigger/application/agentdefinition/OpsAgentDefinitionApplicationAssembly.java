package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCatalogLoadService;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMemoryCatalog;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMutationService;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionQueryService;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionSourceLoader;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionGraphRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinitionValidator;

import java.util.function.Supplier;

/** Immutable Application object graph assembled only by the Spring composition root or tests. */
public record OpsAgentDefinitionApplicationAssembly(
        OpsAgentCapabilityBindingQueryAdapter capabilityBindingQueryAdapter,
        AgentDefinitionMutationService<OpsAgentDefinition> mutationService,
        AgentDefinitionCatalogLoadService<OpsAgentDefinition> catalogLoadService,
        OpsAgentDefinitionDescriptorAdapter descriptorAdapter,
        OpsAgentDefinitionCatalogAdapter catalogAdapter,
        OpsProjectAgentDirectoryAdapter projectDirectoryAdapter,
        AgentDefinitionQueryService<OpsAgentDefinition> definitionQueryService) {

    public static OpsAgentDefinitionApplicationAssembly create(
            OpsAgentDefinitionValidator definitionValidator,
            IAgentDefinitionRepository definitionRepository,
            IAgentDefinitionGraphRepository graphRepository,
            IAgentCapabilityBindingRepository capabilityRepository,
            AgentDefinitionSourceLoader<OpsAgentDefinition> yamlDefinitionLoader,
            AgentDefinitionSnapshotMapper<OpsAgentDefinition> snapshotMapper,
            Supplier<ProjectDefinitionApplicationService> projectServiceSupplier,
            Supplier<String> defaultAgentIdSupplier) {
        if (definitionValidator == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_VALIDATOR_REQUIRED");
        }
        if (yamlDefinitionLoader == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_SOURCE_LOADER_REQUIRED");
        }
        if (snapshotMapper == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_SNAPSHOT_MAPPER_REQUIRED");
        }
        if (projectServiceSupplier == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_SUPPLIER_REQUIRED");
        }
        if (defaultAgentIdSupplier == null) {
            throw new IllegalArgumentException("AGENT_DEFAULT_ID_SUPPLIER_REQUIRED");
        }

        OpsAgentDefinitionDescriptorAdapter descriptorAdapter =
                new OpsAgentDefinitionDescriptorAdapter(snapshotMapper);
        AgentDefinitionMemoryCatalog<OpsAgentDefinition> memoryCatalog =
                new AgentDefinitionMemoryCatalog<>(descriptorAdapter);
        OpsAgentDefinitionMutationAdapter mutationAdapter = new OpsAgentDefinitionMutationAdapter(
                definitionValidator,
                snapshotMapper,
                definitionRepository,
                graphRepository,
                capabilityRepository);
        AgentDefinitionMutationService<OpsAgentDefinition> mutationService =
                new AgentDefinitionMutationService<>(mutationAdapter, mutationAdapter, memoryCatalog);
        OpsAgentDefinitionCatalogAdapter catalogAdapter = new OpsAgentDefinitionCatalogAdapter(
                memoryCatalog,
                mutationService,
                defaultAgentIdSupplier);
        OpsProjectAgentDirectoryAdapter projectDirectoryAdapter = new OpsProjectAgentDirectoryAdapter(
                projectServiceSupplier,
                defaultAgentIdSupplier);
        OpsAgentDefinitionCatalogSourceAdapter sourceAdapter = new OpsAgentDefinitionCatalogSourceAdapter(
                yamlDefinitionLoader,
                definitionRepository,
                snapshotMapper);
        AgentDefinitionCatalogLoadService<OpsAgentDefinition> catalogLoadService =
                new AgentDefinitionCatalogLoadService<>(
                        sourceAdapter,
                        sourceAdapter,
                        new OpsAgentDefinitionFallbackFactory(),
                        mutationService,
                        memoryCatalog);
        AgentDefinitionQueryService<OpsAgentDefinition> definitionQueryService =
                new AgentDefinitionQueryService<>(catalogAdapter, descriptorAdapter, projectDirectoryAdapter);

        return new OpsAgentDefinitionApplicationAssembly(
                new OpsAgentCapabilityBindingQueryAdapter(capabilityRepository),
                mutationService,
                catalogLoadService,
                descriptorAdapter,
                catalogAdapter,
                projectDirectoryAdapter,
                definitionQueryService);
    }
}
