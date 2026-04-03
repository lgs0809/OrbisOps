package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Projects the availability of database-managed Chat models without exposing secrets.
 * Runtime model resolution remains authoritative for the actual call.
 */
@Service
public class OpsConfiguredChatModelAvailabilityService {

    private static final Set<String> PLACEHOLDER_KEYS = Set.of(
            "dev-placeholder-key", "dev-local-placeholder", "placeholder", "dummy",
            "fake-key", "changeme", "change-me", "test");

    private final AiClientModelCatalogPort modelCatalog;
    private final AiClientApiCatalogPort apiCatalog;
    private final OpsSecretResolver secretResolver;

    public OpsConfiguredChatModelAvailabilityService(
            AiClientModelCatalogPort modelCatalog,
            AiClientApiCatalogPort apiCatalog,
            OpsSecretResolver secretResolver) {
        this.modelCatalog = modelCatalog;
        this.apiCatalog = apiCatalog;
        this.secretResolver = secretResolver;
    }

    public boolean anyAvailable() {
        return Boolean.TRUE.equals(status().get("configuredModelAvailable"));
    }

    public Map<String, Object> status() {
        List<AiClientModelDefinition> enabledModels = safeEnabledModels();
        int usable = 0;
        for (AiClientModelDefinition model : enabledModels) {
            if (usable(model)) {
                usable++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabledConfiguredModelCount", enabledModels.size());
        result.put("usableConfiguredModelCount", usable);
        result.put("configuredModelAvailable", usable > 0);
        return result;
    }

    private List<AiClientModelDefinition> safeEnabledModels() {
        try {
            List<AiClientModelDefinition> models = modelCatalog.listEnabled();
            return models == null ? List.of() : models;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private boolean usable(AiClientModelDefinition model) {
        if (model == null
                || !Integer.valueOf(1).equals(model.status())
                || !StringUtils.hasText(model.modelId())
                || !StringUtils.hasText(model.modelName())
                || !StringUtils.hasText(model.apiId())) {
            return false;
        }
        try {
            AiClientApiDefinition api = apiCatalog.findByApiId(model.apiId());
            if (api == null
                    || !Integer.valueOf(1).equals(api.status())
                    || !StringUtils.hasText(api.baseUrl())) {
                return false;
            }
            return usableSecret(secretResolver.resolve(api.apiKey()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean usableSecret(String secret) {
        if (!StringUtils.hasText(secret)) return false;
        String lowered = secret.trim().toLowerCase(Locale.ROOT);
        if (PLACEHOLDER_KEYS.contains(lowered)) return false;
        return !lowered.contains("placeholder")
                && !lowered.contains("dummy")
                && !lowered.contains("fake");
    }
}
