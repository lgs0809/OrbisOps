package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelSyncFetchResult;
import cn.lgs.orbisops.application.config.AiClientModelSyncProtocolPort;
import cn.lgs.orbisops.application.config.AiClientModelSyncTarget;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** OpenAI-compatible `/models` HTTP protocol adapter. */
public final class OpsAiClientModelSyncHttpProtocolAdapter implements AiClientModelSyncProtocolPort {

    private final HttpClient httpClient;

    public OpsAiClientModelSyncHttpProtocolAdapter() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    public OpsAiClientModelSyncHttpProtocolAdapter(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HTTP_CLIENT_REQUIRED");
        }
        this.httpClient = httpClient;
    }

    @Override
    public String resolveEndpoint(AiClientModelSyncTarget target) {
        String baseUrl = target == null ? null : target.baseUrl();
        if (!hasText(baseUrl)) {
            throw new IllegalArgumentException("Provider Base URL 为空");
        }
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/models")) {
            return normalized;
        }
        return normalized.replaceAll("/+$", "") + "/models";
    }

    @Override
    public AiClientModelSyncFetchResult fetch(AiClientModelSyncTarget target, String endpoint) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .header("Accept", "application/json");
            if (target != null && hasText(target.apiKey())) {
                requestBuilder.header("Authorization", "Bearer " + target.apiKey());
            }
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Provider /models HTTP " + response.statusCode() + ": " + truncate(response.body(), 300));
            }
            return new AiClientModelSyncFetchResult(
                    endpoint,
                    response.statusCode(),
                    parseModelIds(response.body()));
        } catch (RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private List<String> parseModelIds(String body) {
        JSONObject object = JSON.parseObject(body);
        JSONArray data = object.getJSONArray("data");
        if (data == null) {
            return List.of();
        }
        List<String> modelIds = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            String id = item == null ? null : item.getString("id");
            if (hasText(id) && !modelIds.contains(id.trim())) {
                modelIds.add(id.trim());
            }
        }
        return modelIds;
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
