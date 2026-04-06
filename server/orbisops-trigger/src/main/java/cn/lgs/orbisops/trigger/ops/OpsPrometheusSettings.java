package cn.lgs.orbisops.trigger.ops;

/** Typed Prometheus datasource settings for the metric sub-agent. */
public record OpsPrometheusSettings(
        String baseUrl,
        String jobName,
        int timeoutSeconds) {

    public OpsPrometheusSettings {
        baseUrl = stripTrailingSlash(text(baseUrl));
        jobName = text(jobName);
        timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public static OpsPrometheusSettings defaults() {
        return new OpsPrometheusSettings("http://127.0.0.1:9090", "", 5);
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
