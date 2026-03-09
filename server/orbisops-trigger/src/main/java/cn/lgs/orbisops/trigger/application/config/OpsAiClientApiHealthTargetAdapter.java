package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientApiHealthTarget;
import cn.lgs.orbisops.application.config.AiClientApiHealthTargetPort;

/** Resolves health targets from the existing typed provider catalog. */
public final class OpsAiClientApiHealthTargetAdapter implements AiClientApiHealthTargetPort {

    private final AiClientApiCatalogUseCase catalogUseCase;

    public OpsAiClientApiHealthTargetAdapter(AiClientApiCatalogUseCase catalogUseCase) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_USE_CASE_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
    }

    @Override
    public AiClientApiHealthTarget find(String apiId) {
        AiClientApiDefinition definition = catalogUseCase.findByApiId(apiId);
        return definition == null
                ? null
                : new AiClientApiHealthTarget(
                        definition.apiId(), definition.baseUrl(), definition.apiKey());
    }
}
