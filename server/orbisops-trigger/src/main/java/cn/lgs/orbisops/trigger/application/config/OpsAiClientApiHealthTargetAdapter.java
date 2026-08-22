package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientApiHealthTarget;
import cn.lgs.orbisops.application.config.AiClientApiHealthTargetPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;

/** Resolves health targets from the existing typed provider catalog. */
public final class OpsAiClientApiHealthTargetAdapter implements AiClientApiHealthTargetPort {

    private final AiClientApiCatalogUseCase catalogUseCase;
    private final OpsSecretResolver secretResolver;

    public OpsAiClientApiHealthTargetAdapter(AiClientApiCatalogUseCase catalogUseCase, OpsSecretResolver secretResolver) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_USE_CASE_REQUIRED");
        }
        if (secretResolver == null) {
            throw new IllegalArgumentException("SECRET_RESOLVER_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
        this.secretResolver = secretResolver;
    }

    @Override
    public AiClientApiHealthTarget find(String apiId) {
        AiClientApiDefinition definition = catalogUseCase.findByApiId(apiId);
        return definition == null
                ? null
                : new AiClientApiHealthTarget(
                        definition.apiId(),
                        definition.baseUrl(),
                        definition.completionsPath(),
                        secretResolver.resolve(definition.apiKey()));
    }
}
