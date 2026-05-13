package cn.lgs.orbisops.trigger.ops.rag;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/** OpenAI-compatible visual analysis protocol ACL. */
public final class RagVisualAnalysisProtocol {

    private final RagVisualAnalysisSettings settings;
    private final HttpTransport transport;
    private final RetryDelay retryDelay;

    public RagVisualAnalysisProtocol(RagVisualAnalysisSettings settings) {
        this(
                settings,
                defaultTransport(),
                attempt -> Thread.sleep(Math.min(3000L, 500L * attempt)));
    }

    private static HttpTransport defaultTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return request -> {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new TransportResponse(response.statusCode(), response.body());
        };
    }

    RagVisualAnalysisProtocol(
            RagVisualAnalysisSettings settings,
            HttpTransport transport,
            RetryDelay retryDelay) {
        if (settings == null) throw new IllegalArgumentException("RAG_VISUAL_SETTINGS_REQUIRED");
        if (transport == null) throw new IllegalArgumentException("RAG_VISUAL_HTTP_TRANSPORT_REQUIRED");
        if (retryDelay == null) throw new IllegalArgumentException("RAG_VISUAL_RETRY_DELAY_REQUIRED");
        this.settings = settings;
        this.transport = transport;
        this.retryDelay = retryDelay;
    }

    public AnalysisResponse analyze(byte[] imageBytes, String mimeType) throws Exception {
        JSONObject response = postJson(requestBody(imageBytes, mimeType).toJSONString());
        JSONObject message = response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message");
        if (message.containsKey("refusal")) {
            return new AnalysisResponse(true, message.getString("refusal"), "");
        }
        return new AnalysisResponse(false, "", message.getString("content"));
    }

    JSONObject requestBody(byte[] imageBytes, String mimeType) {
        JSONObject body = new JSONObject(true);
        body.put("model", settings.model());
        body.put("temperature", 0);
        applyTokenLimit(body);
        JSONObject responseFormat = responseFormat();
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }

        JSONArray messages = new JSONArray();
        messages.add(message("system", """
                你是运维知识库的视觉文档解析器。把截图、扫描件、图表、流程图、表格图片解析为适合 RAG 入库的结构化 JSON。
                重点提取：可见文字、表格字段关系、接口/错误码/指标/日志关键字、证据边界和不确定性。
                不要编造看不清的内容；看不清时写入 evidence_notes。
                """));

        JSONArray userContent = new JSONArray();
        JSONObject text = new JSONObject(true);
        text.put("type", "text");
        text.put("text", "请解析这张运维文档图片，输出符合 schema 的 JSON。");
        userContent.add(text);

        JSONObject image = new JSONObject(true);
        image.put("type", "image_url");
        JSONObject imageUrl = new JSONObject(true);
        imageUrl.put("url", "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes));
        imageUrl.put("detail", settings.detail());
        image.put("image_url", imageUrl);
        userContent.add(image);

        JSONObject user = new JSONObject(true);
        user.put("role", "user");
        user.put("content", userContent);
        messages.add(user);
        body.put("messages", messages);
        return body;
    }

    private JSONObject responseFormat() {
        String mode = settings.responseFormatMode();
        if ("none".equals(mode) || "false".equals(mode) || "disabled".equals(mode)) {
            return null;
        }
        if ("json_object".equals(mode) || "json".equals(mode)) {
            JSONObject responseFormat = new JSONObject(true);
            responseFormat.put("type", "json_object");
            return responseFormat;
        }

        JSONObject tableSchema = objectSchema(
                property("title", stringSchema()),
                property("markdown", stringSchema()),
                property("confidence", numberSchema()));
        tableSchema.put("required", List.of("title", "markdown", "confidence"));

        JSONObject keyValueSchema = objectSchema(
                property("key", stringSchema()),
                property("value", stringSchema()),
                property("confidence", numberSchema()));
        keyValueSchema.put("required", List.of("key", "value", "confidence"));

        JSONObject schema = objectSchema(
                property("title", stringSchema()),
                property("summary", stringSchema()),
                property("ocr_text", stringSchema()),
                property("tables", arraySchema(tableSchema)),
                property("key_values", arraySchema(keyValueSchema)),
                property("operations_signals", arraySchema(stringSchema())),
                property("evidence_notes", arraySchema(stringSchema())),
                property("confidence", numberSchema()));
        schema.put("required", List.of(
                "title",
                "summary",
                "ocr_text",
                "tables",
                "key_values",
                "operations_signals",
                "evidence_notes",
                "confidence"));

        JSONObject jsonSchema = new JSONObject(true);
        jsonSchema.put("name", "ops_visual_document_extraction");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);

        JSONObject responseFormat = new JSONObject(true);
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);
        return responseFormat;
    }

    private void applyTokenLimit(JSONObject body) {
        int limit = settings.maxCompletionTokens();
        String field = settings.tokenLimitField();
        if ("max_tokens".equals(field)) {
            body.put("max_tokens", limit);
            return;
        }
        if ("both".equalsIgnoreCase(field)) {
            body.put("max_completion_tokens", limit);
            body.put("max_tokens", limit);
            return;
        }
        body.put("max_completion_tokens", limit);
    }

    private JSONObject postJson(String body) throws Exception {
        int attempts = settings.maxRetries() + 1;
        Exception lastException = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.endpoint()))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            try {
                TransportResponse response = transport.send(request);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return JSONObject.parseObject(response.body());
                }
                IllegalStateException exception = new IllegalStateException(
                        "Visual parse HTTP " + response.statusCode() + " "
                                + abbreviate(response.body(), 180));
                if (!retryable(response.statusCode()) || attempt == attempts) {
                    throw exception;
                }
                lastException = exception;
            } catch (Exception e) {
                if (attempt == attempts) {
                    throw e;
                }
                lastException = e;
            }
            sleepBeforeRetry(attempt);
        }
        throw lastException == null
                ? new IllegalStateException("Visual parse failed")
                : lastException;
    }

    private boolean retryable(int statusCode) {
        return statusCode == 408
                || statusCode == 409
                || statusCode == 429
                || statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedException {
        try {
            retryDelay.sleep(attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private JSONObject message(String role, String content) {
        JSONObject message = new JSONObject(true);
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private JSONObject objectSchema(JSONObject... properties) {
        JSONObject schema = new JSONObject(true);
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        JSONObject props = new JSONObject(true);
        for (JSONObject property : properties) {
            props.put(property.getString("name"), property.get("schema"));
        }
        schema.put("properties", props);
        return schema;
    }

    private JSONObject property(String name, JSONObject schema) {
        JSONObject property = new JSONObject(true);
        property.put("name", name);
        property.put("schema", schema);
        return property;
    }

    private JSONObject stringSchema() {
        JSONObject schema = new JSONObject(true);
        schema.put("type", "string");
        return schema;
    }

    private JSONObject numberSchema() {
        JSONObject schema = new JSONObject(true);
        schema.put("type", "number");
        return schema;
    }

    private JSONObject arraySchema(JSONObject itemSchema) {
        JSONObject schema = new JSONObject(true);
        schema.put("type", "array");
        schema.put("items", itemSchema);
        return schema;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public record AnalysisResponse(
            boolean refused,
            String refusal,
            String content) {
    }

    record TransportResponse(int statusCode, String body) {
    }

    @FunctionalInterface
    interface HttpTransport {
        TransportResponse send(HttpRequest request) throws Exception;
    }

    @FunctionalInterface
    interface RetryDelay {
        void sleep(int attempt) throws InterruptedException;
    }
}
