package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelSyncTarget;
import cn.lgs.orbisops.application.config.AiClientModelSyncTargetPort;

/** Resolves Provider sync targets through the typed API catalog. */
public final class OpsAiClientModelSyncTargetAdapter implements AiClientModelSyncTargetPort {

    private final AiClientApiCatalogUseCase apiCatalogUseCase;

    public OpsAiClientModelSyncTargetAdapter(AiClientApiCatalogUseCase apiCatalogUseCase) {
        if (apiCatalogUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_USE_CASE_REQUIRED");
        }
        this.apiCatalogUseCase = apiCatalogUseCase;
    }

    @Override
    public AiClientModelSyncTarget find(String apiId) {
        AiClientApiDefinition api = apiCatalogUseCase.findByApiId(apiId);
        if (api == null) {
            return null;
        }
        return new AiClientModelSyncTarget(api.apiId(), api.baseUrl(), api.apiKey());
    }
}
