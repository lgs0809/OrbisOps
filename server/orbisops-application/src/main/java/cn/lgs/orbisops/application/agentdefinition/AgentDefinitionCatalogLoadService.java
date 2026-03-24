package cn.lgs.orbisops.application.agentdefinition;

import java.util.ArrayList;
import java.util.List;

/** Application service that owns Agent Definition source precedence and catalog initialization. */
public final class AgentDefinitionCatalogLoadService<D> {

    private final AgentDefinitionCatalogSourcePort<D> sourcePort;
    private final AgentDefinitionCatalogLoadModelPort<D> modelPort;
    private final AgentDefinitionFallbackFactory<D> fallbackFactory;
    private final AgentDefinitionMutationService<D> mutationService;
    private final AgentDefinitionMemoryCatalog<D> memoryCatalog;

    public AgentDefinitionCatalogLoadService(AgentDefinitionCatalogSourcePort<D> sourcePort,
                                             AgentDefinitionCatalogLoadModelPort<D> modelPort,
                                             AgentDefinitionFallbackFactory<D> fallbackFactory,
                                             AgentDefinitionMutationService<D> mutationService,
                                             AgentDefinitionMemoryCatalog<D> memoryCatalog) {
        if (sourcePort == null) throw new IllegalArgumentException("AGENT_DEFINITION_SOURCE_PORT_REQUIRED");
        if (modelPort == null) throw new IllegalArgumentException("AGENT_DEFINITION_LOAD_MODEL_PORT_REQUIRED");
        if (fallbackFactory == null) throw new IllegalArgumentException("AGENT_DEFINITION_FALLBACK_FACTORY_REQUIRED");
        if (mutationService == null) throw new IllegalArgumentException("AGENT_DEFINITION_MUTATION_SERVICE_REQUIRED");
        if (memoryCatalog == null) throw new IllegalArgumentException("AGENT_DEFINITION_MEMORY_CATALOG_REQUIRED");
        this.sourcePort = sourcePort;
        this.modelPort = modelPort;
        this.fallbackFactory = fallbackFactory;
        this.mutationService = mutationService;
        this.memoryCatalog = memoryCatalog;
    }

    public AgentDefinitionCatalogLoadResult load(String locations,
                                                 String requestedDefaultAgentId,
                                                 String fallbackAgentId) {
        String normalizedFallbackAgentId = required(fallbackAgentId, "AGENT_FALLBACK_DEFINITION_REQUIRED");
        List<String> rejectedProjectPlatformAgentIds = new ArrayList<>();
        List<String> skippedStoredYamlCurrentAgentIds = new ArrayList<>();
        List<String> skippedStoredYamlVersionKeys = new ArrayList<>();

        memoryCatalog.clear();
        for (D definition : safe(sourcePort.loadPlatformDefinitions(locations))) {
            if (definition == null || !hasText(modelPort.agentId(definition))) {
                continue;
            }
            if (hasText(modelPort.projectId(definition))) {
                rejectedProjectPlatformAgentIds.add(modelPort.agentId(definition).trim());
                continue;
            }
            mutationService.registerLoaded(definition, true);
        }

        List<D> storedCurrentDefinitions = safe(sourcePort.loadStoredCurrentDefinitions());
        for (D definition : storedCurrentDefinitions) {
            if (definition == null || !hasText(modelPort.agentId(definition))) {
                continue;
            }
            String agentId = modelPort.agentId(definition).trim();
            if (isYaml(definition) && memoryCatalog.containsCurrent(agentId)) {
                skippedStoredYamlCurrentAgentIds.add(agentId);
                continue;
            }
            mutationService.registerStoredSnapshot(definition, true);
        }

        for (D definition : safe(sourcePort.loadStoredVersionDefinitions())) {
            if (definition == null || !hasText(modelPort.agentId(definition))) {
                continue;
            }
            Integer version = modelPort.version(definition);
            String agentId = modelPort.agentId(definition).trim();
            if (isYaml(definition) && version != null && version > 0
                    && memoryCatalog.containsVersion(agentId, version)) {
                skippedStoredYamlVersionKeys.add(agentId + "@" + version);
                continue;
            }
            mutationService.registerStoredSnapshot(definition, false);
        }

        D fallbackDefinition = fallbackFactory.create();
        if (fallbackDefinition == null
                || !normalizedFallbackAgentId.equals(trim(modelPort.agentId(fallbackDefinition)))) {
            throw new IllegalStateException("AGENT_FALLBACK_DEFINITION_ID_MISMATCH");
        }
        if (!memoryCatalog.containsCurrent(normalizedFallbackAgentId)) {
            mutationService.registerLoaded(fallbackDefinition, true);
        }

        String requestedDefault = trim(requestedDefaultAgentId);
        String effectiveDefault = hasText(requestedDefault)
                && memoryCatalog.containsCurrent(requestedDefault)
                ? requestedDefault
                : normalizedFallbackAgentId;

        return new AgentDefinitionCatalogLoadResult(
                effectiveDefault,
                memoryCatalog.currentSize(),
                storedCurrentDefinitions.size(),
                rejectedProjectPlatformAgentIds,
                skippedStoredYamlCurrentAgentIds,
                skippedStoredYamlVersionKeys);
    }

    private boolean isYaml(D definition) {
        return "YAML".equalsIgnoreCase(trim(modelPort.source(definition)));
    }

    private List<D> safe(List<D> values) {
        return values == null ? List.of() : values;
    }

    private String required(String value, String error) {
        String normalized = trim(value);
        if (!hasText(normalized)) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
