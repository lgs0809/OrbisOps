package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** External source boundary used to initialize the Agent Definition catalog. */
public interface AgentDefinitionCatalogSourcePort<D> {

    List<D> loadPlatformDefinitions(String locations);

    List<D> loadStoredCurrentDefinitions();

    List<D> loadStoredVersionDefinitions();
}
