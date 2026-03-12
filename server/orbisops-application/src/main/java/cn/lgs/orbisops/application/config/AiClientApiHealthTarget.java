package cn.lgs.orbisops.application.config;

/** Provider connection details required by one health probe. */
public record AiClientApiHealthTarget(
        String apiId,
        String baseUrl,
        String completionsPath,
        String apiKey) {
}
