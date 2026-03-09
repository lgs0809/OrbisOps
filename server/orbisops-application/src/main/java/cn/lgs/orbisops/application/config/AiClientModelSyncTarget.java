package cn.lgs.orbisops.application.config;

/** Provider connection facts required for one model-catalog synchronization. */
public record AiClientModelSyncTarget(
        String apiId,
        String baseUrl,
        String apiKey) {
}
