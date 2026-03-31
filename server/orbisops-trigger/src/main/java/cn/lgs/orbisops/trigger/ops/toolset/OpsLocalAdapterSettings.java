package cn.lgs.orbisops.trigger.ops.toolset;

/** Typed configuration for local operations adapter protocols. */
public record OpsLocalAdapterSettings(
        String prometheusUrl,
        String elasticsearchUrl,
        String elasticsearchIndex,
        String elasticsearchIndexWhitelist,
        String allowedLogRoots,
        String allowedDockerComposeRoots,
        int timeoutSeconds,
        int maxRows,
        int maxResponseBytes) {

    public OpsLocalAdapterSettings {
        prometheusUrl = stripTrailingSlash(text(prometheusUrl, "http://127.0.0.1:9090"));
        elasticsearchUrl = stripTrailingSlash(text(elasticsearchUrl, "http://127.0.0.1:9200"));
        elasticsearchIndex = text(elasticsearchIndex, "");
        elasticsearchIndexWhitelist = text(elasticsearchIndexWhitelist, "");
        allowedLogRoots = text(allowedLogRoots, "./logs");
        allowedDockerComposeRoots = text(allowedDockerComposeRoots, "./");
        timeoutSeconds = Math.max(1, timeoutSeconds);
        maxRows = Math.max(1, maxRows);
        maxResponseBytes = Math.max(1024, maxResponseBytes);
    }

    public static OpsLocalAdapterSettings defaults() {
        return new OpsLocalAdapterSettings(
                "http://127.0.0.1:9090",
                "http://127.0.0.1:9200",
                "",
                "",
                "./logs",
                "./",
                8,
                200,
                65_536);
    }

    private static String text(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/") && !normalized.isEmpty()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
