package cn.lgs.orbisops.application.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/** Application startup process for creating or safely migrating the default model API entry. */
public final class DefaultModelApiBootstrapUseCase {

    private static final String ENV_REFERENCE = "${env:OPENAI_API_KEY:}";

    private final AiClientApiCatalogPort catalogPort;
    private final Clock clock;

    public DefaultModelApiBootstrapUseCase(AiClientApiCatalogPort catalogPort) {
        this(catalogPort, Clock.systemDefaultZone());
    }

    public DefaultModelApiBootstrapUseCase(
            AiClientApiCatalogPort catalogPort,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("DEFAULT_MODEL_API_BOOTSTRAP_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.clock = clock;
    }

    public DefaultModelApiBootstrapResult bootstrap(DefaultModelApiBootstrapPlan plan) {
        if (!usable(plan)) {
            return skipped(plan);
        }
        String apiId = text(plan.apiId(), "1001");
        String baseUrl = trimTrailingSlash(plan.configuredBaseUrl());
        AiClientApiDefinition existing = catalogPort.findByApiId(apiId);
        if (existing == null) {
            LocalDateTime now = LocalDateTime.now(clock);
            catalogPort.insert(new AiClientApiDefinition(
                    null,
                    apiId,
                    text(apiId, "default-provider"),
                    "OPENAI_COMPATIBLE",
                    baseUrl,
                    ENV_REFERENCE,
                    "v1/chat/completions",
                    "v1/embeddings",
                    1,
                    now,
                    now));
            return new DefaultModelApiBootstrapResult(
                    DefaultModelApiBootstrapResult.Action.CREATED,
                    apiId,
                    baseUrl);
        }
        if (!placeholder(existing.apiKey())) {
            return new DefaultModelApiBootstrapResult(
                    DefaultModelApiBootstrapResult.Action.SKIPPED,
                    apiId,
                    baseUrl);
        }
        catalogPort.updateByApiId(new AiClientApiDefinition(
                existing.id(),
                apiId,
                text(apiId, "default-provider"),
                "OPENAI_COMPATIBLE",
                baseUrl,
                ENV_REFERENCE,
                text(existing.completionsPath(), "v1/chat/completions"),
                text(existing.embeddingsPath(), "v1/embeddings"),
                existing.status() == null ? 1 : existing.status(),
                existing.createTime(),
                LocalDateTime.now(clock)));
        return new DefaultModelApiBootstrapResult(
                DefaultModelApiBootstrapResult.Action.MIGRATED,
                apiId,
                baseUrl);
    }

    private boolean usable(DefaultModelApiBootstrapPlan plan) {
        return plan != null
                && plan.enabled()
                && hasText(plan.configuredBaseUrl())
                && hasText(plan.configuredApiKey())
                && !placeholder(plan.configuredApiKey());
    }

    private DefaultModelApiBootstrapResult skipped(DefaultModelApiBootstrapPlan plan) {
        return new DefaultModelApiBootstrapResult(
                DefaultModelApiBootstrapResult.Action.SKIPPED,
                plan == null ? null : plan.apiId(),
                plan == null ? null : trimTrailingSlash(plan.configuredBaseUrl()));
    }

    private boolean placeholder(String value) {
        if (!hasText(value)) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("replace_with")
                || normalized.contains("placeholder")
                || normalized.equals("test-api-key")
                || normalized.equals("example-api-key");
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String text(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
