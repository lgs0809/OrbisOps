package cn.lgs.orbisops.application.config;

/** Model persistence boundary used by Provider synchronization without per-model audit. */
public interface AiClientModelSyncCatalogPort {

    AiClientModelDefinition findByModelId(String modelId);

    boolean insert(AiClientModelDefinition definition);

    boolean updateByModelId(AiClientModelDefinition definition);
}
