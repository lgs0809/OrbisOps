package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionCatalogPort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMemoryCatalog;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMutationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.List;
import java.util.function.Supplier;

/** Trigger catalog adapter backed by the Application memory catalog and mutation query boundary. */
public final class OpsAgentDefinitionCatalogAdapter
        implements AgentDefinitionCatalogPort<OpsAgentDefinition> {

    private final AgentDefinitionMemoryCatalog<OpsAgentDefinition> memoryCatalog;
    private final AgentDefinitionMutationService<OpsAgentDefinition> mutationService;
    private final Supplier<String> defaultAgentIdSupplier;

    public OpsAgentDefinitionCatalogAdapter(
            AgentDefinitionMemoryCatalog<OpsAgentDefinition> memoryCatalog,
            AgentDefinitionMutationService<OpsAgentDefinition> mutationService,
            Supplier<String> defaultAgentIdSupplier) {
        if (memoryCatalog == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MEMORY_CATALOG_REQUIRED");
        }
        if (mutationService == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_MUTATION_SERVICE_REQUIRED");
        }
        if (defaultAgentIdSupplier == null) {
            throw new IllegalArgumentException("AGENT_DEFAULT_ID_SUPPLIER_REQUIRED");
        }
        this.memoryCatalog = memoryCatalog;
        this.mutationService = mutationService;
        this.defaultAgentIdSupplier = defaultAgentIdSupplier;
    }

    @Override
    public String defaultAgentId() {
        return defaultAgentIdSupplier.get();
    }

    @Override
    public OpsAgentDefinition findCurrent(String agentId) {
        return memoryCatalog.current(agentId);
    }

    @Override
    public OpsAgentDefinition findVersion(String agentId, int version) {
        return mutationService.findVersion(agentId, version).orElse(null);
    }

    @Override
    public List<OpsAgentDefinition> findCurrentDefinitions() {
        return memoryCatalog.currentDefinitions();
    }

    @Override
    public List<OpsAgentDefinition> findVersions(String agentId) {
        return memoryCatalog.versions(agentId);
    }
}
