package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiHealthProbeOutcome;
import cn.lgs.orbisops.application.config.AiClientApiHealthProbePort;
import cn.lgs.orbisops.application.config.AiClientApiHealthTarget;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** HTTP protocol adapter for the OpenAI-compatible /models health probe. */
public final class OpsAiClientApiHealthHttpProbeAdapter implements AiClientApiHealthProbePort {

    private final HttpClient httpClient;

    public OpsAiClientApiHealthHttpProbeAdapter(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_HTTP_CLIENT_REQUIRED");
        }
        this.httpClient = httpClient;
    }

    @Override
    public AiClientApiHealthProbeOutcome probe(AiClientApiHealthTarget target) {
        long startNanos = System.nanoTime();
        String endpoint = resolveModelsEndpoint(target == null ? null : target.baseUrl());
        try {
            if (!hasText(endpoint)) {
                throw new IllegalArgumentException("Provider Base URL 为空，无法检测");
            }
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .header("Accept", "application/json");
            if (target != null && hasText(target.apiKey())) {
                request.header("Authorization", "Bearer " + target.apiKey());
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new AiClientApiHealthProbeOutcome(
                    maskEndpoint(endpoint),
                    success ? "SUCCESS" : "FAILED",
                    response.statusCode(),
                    elapsedMillis(startNanos),
                    success ? "" : truncate(
                            "HTTP " + response.statusCode() + ": " + response.body(), 500));
        } catch (Exception exception) {
            return new AiClientApiHealthProbeOutcome(
                    maskEndpoint(endpoint),
                    "FAILED",
                    null,
                    elapsedMillis(startNanos),
                    truncate(exception.getMessage(), 500));
        }
    }

    private String resolveModelsEndpoint(String baseUrl) {
        if (!hasText(baseUrl)) {
            return "";
        }
        String normalized = baseUrl.trim();
        return normalized.endsWith("/models")
                ? normalized
                : normalized.replaceAll("/+$", "") + "/models";
    }

    private String maskEndpoint(String endpoint) {
        if (!hasText(endpoint)) {
            return "";
        }
        return endpoint.replaceAll(
                "(?i)(api[_-]?key|token|secret)=([^&]+)",
                "$1=******");
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
