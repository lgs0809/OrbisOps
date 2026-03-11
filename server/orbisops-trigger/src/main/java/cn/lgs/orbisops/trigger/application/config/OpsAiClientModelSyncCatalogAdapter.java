package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.application.config.AiClientModelSyncCatalogPort;

/** Narrow synchronization adapter over the typed model catalog. */
public final class OpsAiClientModelSyncCatalogAdapter implements AiClientModelSyncCatalogPort {

    private final AiClientModelCatalogPort catalogPort;

    public OpsAiClientModelSyncCatalogAdapter(AiClientModelCatalogPort catalogPort) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_CATALOG_PORT_REQUIRED");
        }
        this.catalogPort = catalogPort;
    }

    @Override
    public AiClientModelDefinition findByModelId(String modelId) {
        return catalogPort.findByModelId(modelId);
    }

    @Override
    public boolean insert(AiClientModelDefinition definition) {
        return catalogPort.insert(definition);
    }

    @Override
    public boolean updateByModelId(AiClientModelDefinition definition) {
        return catalogPort.updateByModelId(definition);
    }
}
