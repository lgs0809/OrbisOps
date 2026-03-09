package cn.lgs.orbisops.trigger.ops.rag;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Protocol ACL for multimodal text/image embedding requests. */
public final class RagMultimodalEmbeddingProtocol {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int ERROR_BODY_LIMIT = 180;

    private final RagMultimodalSettings settings;
    private final HttpTransport httpTransport;
    private final RetryDelay retryDelay;

    public RagMultimodalEmbeddingProtocol(RagMultimodalSettings settings) {
        this(settings, defaultTransport(), defaultRetryDelay());
    }

    RagMultimodalEmbeddingProtocol(
            RagMultimodalSettings settings,
            HttpTransport httpTransport,
            RetryDelay retryDelay) {
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        if (httpTransport == null) throw new IllegalArgumentException("RAG_MULTIMODAL_HTTP_TRANSPORT_REQUIRED");
        if (retryDelay == null) throw new IllegalArgumentException("RAG_MULTIMODAL_RETRY_DELAY_REQUIRED");
        this.settings = settings;
        this.httpTransport = httpTransport;
        this.retryDelay = retryDelay;
    }

    public List<Double> embedText(String text, String inputType) throws Exception {
        JSONArray content = new JSONArray();
        JSONObject item = new JSONObject(true);
        item.put("type", "text");
        item.put("text", abbreviate(text, settings.maxTextChars()));
        content.add(item);
        return embed(content, inputType);
    }

    public List<Double> embedImage(
            String textContext,
            byte[] imageBytes,
            String mimeType,
            String inputType) throws Exception {
        JSONArray content = new JSONArray();
        if (hasText(textContext)) {
            JSONObject text = new JSONObject(true);
            text.put("type", "text");
            text.put("text", abbreviate(textContext, settings.maxTextChars()));
            content.add(text);
        }
        JSONObject image = new JSONObject(true);
        image.put("type", "image_base64");
        image.put("image_base64", "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes));
        content.add(image);
        return embed(content, inputType);
    }

    private List<Double> embed(JSONArray content, String inputType) throws Exception {
        JSONObject input = new JSONObject(true);
        input.put("content", content);

        JSONArray inputs = new JSONArray();
        inputs.add(input);

        JSONObject body = new JSONObject(true);
        body.put("inputs", inputs);
        body.put("model", settings.model());
        body.put("input_type", hasText(inputType) ? inputType : "document");
        body.put("truncation", true);
        if (settings.dimension() != 1024) {
            body.put("output_dimension", settings.dimension());
        }

        JSONObject response = postJson(settings.endpoint(), body.toJSONString());
        return parseEmbedding(response);
    }

    private JSONObject postJson(String url, String body) throws Exception {
        int attempts = settings.maxRetries() + 1;
        Exception lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResult response = httpTransport.post(
                        URI.create(url),
                        body,
                        settings.apiKey(),
                        settings.timeoutSeconds());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return JSONObject.parseObject(response.body());
                }
                IllegalStateException exception = new IllegalStateException(
                        "Multimodal embedding HTTP " + response.statusCode() + " "
                                + abbreviate(response.body(), ERROR_BODY_LIMIT));
                if (!retryable(response.statusCode()) || attempt == attempts) {
                    throw exception;
                }
                lastException = exception;
            } catch (Exception exception) {
                if (attempt == attempts) {
                    throw exception;
                }
                lastException = exception;
            }
            retryDelay.pause(attempt);
        }
        throw lastException == null
                ? new IllegalStateException("Multimodal embedding failed")
                : lastException;
    }

    private List<Double> parseEmbedding(JSONObject response) {
        JSONArray embeddings = response.getJSONArray("embeddings");
        if (embeddings != null && !embeddings.isEmpty()) {
            return toDoubleList(embeddings.getJSONArray(0));
        }
        JSONArray data = response.getJSONArray("data");
        if (data != null && !data.isEmpty()) {
            JSONObject first = data.getJSONObject(0);
            return toDoubleList(first.getJSONArray("embedding"));
        }
        throw new IllegalStateException("Multimodal embedding response does not contain embeddings");
    }

    private List<Double> toDoubleList(JSONArray values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Double> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(values.getDoubleValue(index));
        }
        return result;
    }

    private boolean retryable(int statusCode) {
        return statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500;
    }

    private static HttpTransport defaultTransport() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        return (uri, body, apiKey, timeoutSeconds) -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        };
    }

    private static RetryDelay defaultRetryDelay() {
        return attempt -> {
            try {
                Thread.sleep(Math.min(3000L, 500L * attempt));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            }
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    @FunctionalInterface
    interface HttpTransport {
        HttpResult post(URI uri, String body, String apiKey, int timeoutSeconds) throws Exception;
    }

    @FunctionalInterface
    interface RetryDelay {
        void pause(int attempt) throws InterruptedException;
    }

    record HttpResult(int statusCode, String body) {
    }
}
