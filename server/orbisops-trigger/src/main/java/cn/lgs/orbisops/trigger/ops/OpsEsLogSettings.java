package cn.lgs.orbisops.trigger.ops;

/** Typed Elasticsearch log datasource settings for the ES sub-agent. */
public record OpsEsLogSettings(
        String baseUrl,
        String index,
        int timeoutSeconds,
        int sampleSize) {

    public OpsEsLogSettings {
        baseUrl = stripTrailingSlash(text(baseUrl));
        index = text(index);
        timeoutSeconds = Math.max(1, timeoutSeconds);
        sampleSize = Math.max(1, Math.min(sampleSize, 100));
    }

    public static OpsEsLogSettings defaults() {
        return new OpsEsLogSettings("http://127.0.0.1:9200", "", 5, 8);
    }

    public String endpoint() {
        return index.isBlank() ? baseUrl : baseUrl + "/" + index;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
