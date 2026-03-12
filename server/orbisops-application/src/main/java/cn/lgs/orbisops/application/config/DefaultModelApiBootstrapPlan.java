package cn.lgs.orbisops.application.config;

/** Typed startup plan for the optional default model API configuration. */
public record DefaultModelApiBootstrapPlan(
        boolean enabled,
        String apiId,
        String configuredBaseUrl,
        String configuredApiKey) {
}
