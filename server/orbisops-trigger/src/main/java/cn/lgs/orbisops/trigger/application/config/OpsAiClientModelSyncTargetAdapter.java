package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelSyncTarget;
import cn.lgs.orbisops.application.config.AiClientModelSyncTargetPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;

/** Resolves Provider sync targets through the typed API catalog. */
public final class OpsAiClientModelSyncTargetAdapter implements AiClientModelSyncTargetPort {

    private final AiClientApiCatalogUseCase apiCatalogUseCase;
    private final OpsSecretResolver secretResolver;

    public OpsAiClientModelSyncTargetAdapter(AiClientApiCatalogUseCase apiCatalogUseCase, OpsSecretResolver secretResolver) {
        if (apiCatalogUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_USE_CASE_REQUIRED");
        }
        if (secretResolver == null) {
            throw new IllegalArgumentException("SECRET_RESOLVER_REQUIRED");
        }
        this.apiCatalogUseCase = apiCatalogUseCase;
        this.secretResolver = secretResolver;
    }

    @Override
    public AiClientModelSyncTarget find(String apiId) {
        AiClientApiDefinition api = apiCatalogUseCase.findByApiId(apiId);
        if (api == null) {
            return null;
        }
        return new AiClientModelSyncTarget(api.apiId(), api.baseUrl(), secretResolver.resolve(api.apiKey()));
    }
}
