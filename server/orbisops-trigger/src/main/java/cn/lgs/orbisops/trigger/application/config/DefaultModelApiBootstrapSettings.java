package cn.lgs.orbisops.trigger.application.config;

/** Typed bootstrap policy for the default OpenAI-compatible API record. */
public record DefaultModelApiBootstrapSettings(
        boolean enabled,
        String apiId,
        String configuredBaseUrl,
        String configuredApiKey) {

    public DefaultModelApiBootstrapSettings {
        apiId = text(apiId);
        configuredBaseUrl = text(configuredBaseUrl);
        configuredApiKey = text(configuredApiKey);
    }

    public static DefaultModelApiBootstrapSettings defaults() {
        return new DefaultModelApiBootstrapSettings(false, "1001", "", "");
    }

    static DefaultModelApiBootstrapSettings legacyConstructorDefaults() {
        return new DefaultModelApiBootstrapSettings(false, "", "", "");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
