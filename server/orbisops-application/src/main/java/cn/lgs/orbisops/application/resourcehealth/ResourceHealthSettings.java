package cn.lgs.orbisops.application.resourcehealth;

/** Configuration values required by external resource health probes. */
public record ResourceHealthSettings(
        String elasticsearchUrl,
        String elasticsearchIndex,
        String prometheusUrl,
        String prometheusJob,
        String prometheusInstance,
        String ragVectorTableName) {

    public ResourceHealthSettings {
        elasticsearchUrl = text(elasticsearchUrl);
        elasticsearchIndex = text(elasticsearchIndex);
        prometheusUrl = text(prometheusUrl);
        prometheusJob = text(prometheusJob);
        prometheusInstance = text(prometheusInstance);
        ragVectorTableName = text(ragVectorTableName);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
