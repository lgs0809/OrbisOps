package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * LLM query rewrite HTTP/JSON anti-corruption layer.
 */
public final class RagLlmQueryRewriteProtocol {

    private final JsonTransport transport;

    public RagLlmQueryRewriteProtocol() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.transport = (url, body, apiKey, timeoutSeconds) -> {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (hasText(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Query rewrite HTTP " + response.statusCode() + " " + abbreviate(response.body(), 180));
            }
            return response.body();
        };
    }

    RagLlmQueryRewriteProtocol(JsonTransport transport) {
        if (transport == null) {
            throw new IllegalArgumentException("RAG_LLM_QUERY_REWRITE_TRANSPORT_REQUIRED");
        }
        this.transport = transport;
    }

    public RagLlmQueryRewriteResult rewrite(String userText,
                                            Map<String, Object> context,
                                            RagRetrievalSettings configured,
                                            List<String> seedQueries,
                                            boolean lowRecallRetry,
                                            String defaultFilterExpression) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        RagRetrievalSettings settings = configured == null
                ? new RagRetrievalSettings()
                : configured;
        List<String> fallbackQueries = seedQueries == null ? List.of(userText) : seedQueries;

        String baseUrl = valueFromContext(
                safeContext,
                "qa_llm_query_rewrite_base_url",
                settings.getLlmQueryRewriteBaseUrl());
        String apiKey = valueFromContext(
                safeContext,
                "qa_llm_query_rewrite_api_key",
                settings.getLlmQueryRewriteApiKey());
        String path = valueFromContext(
                safeContext,
                "qa_llm_query_rewrite_path",
                settings.getLlmQueryRewritePath());
        String model = valueFromContext(
                safeContext,
                "qa_llm_query_rewrite_model",
                settings.getLlmQueryRewriteModel());
        int maxQueries = clamp(intFromContext(
                safeContext,
                "qa_query_rewrite_max_queries",
                positiveOrDefault(settings.getLlmQueryRewriteMaxQueries(), 4)), 1, 8);
        int timeoutSeconds = clamp(intFromContext(
                safeContext,
                "qa_llm_query_rewrite_timeout_seconds",
                positiveOrDefault(settings.getLlmQueryRewriteTimeoutSeconds(), 2)), 1, 90);

        if (!hasText(baseUrl) || !hasText(apiKey) || !hasText(path) || !hasText(model)) {
            return RagLlmQueryRewriteResult.degraded(
                    fallbackQueries,
                    "RAG LLM query rewrite 配置不完整，无法调用模型",
                    null);
        }

        try {
            JSONArray messages = new JSONArray();
            messages.add(message("system", """
                    你是运维 RAG 检索 query rewrite 模块。你的任务不是回答问题，而是生成用于知识库检索的查询表达式。
                    要求：
                    1. 原始问题必须保留，不能改写或丢弃错误码、traceId、SQL、接口名、指标名、时间范围。
                    2. 可以追加运维领域同义词、英文指标名、数据库字段名、日志关键词。
                    3. 不要编造事实，不要输出解释。
                    4. 只输出 JSON：{"queries":["..."]}，queries 最多 %d 条。
                    """.formatted(maxQueries)));
            JSONObject payload = new JSONObject(true);
            payload.put("originalQuery", userText);
            payload.put("seedQueries", seedQueries == null ? List.of() : seedQueries);
            payload.put("knowledgeFilter", valueFromContext(
                    safeContext,
                    "qa_filter_expression",
                    defaultFilterExpression));
            payload.put("lowRecallRetry", lowRecallRetry);
            payload.put("retrievalGoal", "召回 SOP、故障案例、指标字典、日志字典、架构说明等可作为证据的 chunk");
            messages.add(message("user", payload.toJSONString()));

            JSONObject body = new JSONObject(true);
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0);
            body.put("max_completion_tokens", 600);
            JSONObject responseFormat = new JSONObject(true);
            responseFormat.put("type", "json_object");
            body.put("response_format", responseFormat);

            String responseBody = transport.post(
                    endpoint(baseUrl, path),
                    body.toJSONString(),
                    apiKey,
                    timeoutSeconds);
            JSONObject response = JSONObject.parseObject(responseBody);
            JSONArray choices = response.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return RagLlmQueryRewriteResult.degraded(
                        fallbackQueries,
                        "RAG LLM query rewrite 未返回 choices",
                        null);
            }
            JSONObject responseMessage = choices.getJSONObject(0).getJSONObject("message");
            if (responseMessage == null || !hasText(responseMessage.getString("content"))) {
                return RagLlmQueryRewriteResult.degraded(
                        fallbackQueries,
                        "RAG LLM query rewrite 未返回 content",
                        null);
            }
            JSONObject parsed = parseJsonObject(responseMessage.getString("content"));
            JSONArray queries = parsed.getJSONArray("queries");
            if (queries == null || queries.isEmpty()) {
                return RagLlmQueryRewriteResult.degraded(
                        fallbackQueries,
                        "RAG LLM query rewrite 未返回 queries",
                        null);
            }

            LinkedHashSet<String> merged = new LinkedHashSet<>();
            merged.add(userText);
            if (seedQueries != null) {
                merged.addAll(seedQueries);
            }
            for (int i = 0; i < queries.size(); i++) {
                String query = queries.getString(i);
                if (hasText(query)) {
                    merged.add(abbreviate(query.trim(), 280));
                }
            }
            List<String> rewritten = merged.stream()
                    .filter(RagLlmQueryRewriteProtocol::hasText)
                    .limit(maxQueries)
                    .toList();
            return RagLlmQueryRewriteResult.success(rewritten);
        } catch (Exception e) {
            return RagLlmQueryRewriteResult.degraded(
                    fallbackQueries,
                    "RAG LLM query rewrite 失败：" + e.getMessage(),
                    e);
        }
    }

    private JSONObject message(String role, String content) {
        JSONObject message = new JSONObject(true);
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String endpoint(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return normalizedBaseUrl + "/" + normalizedPath;
    }

    private JSONObject parseJsonObject(String content) {
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized
                    .replaceFirst("^```[a-zA-Z]*\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        if (normalized.startsWith("[")) {
            JSONObject wrapper = new JSONObject(true);
            wrapper.put("results", JSONArray.parseArray(normalized));
            return wrapper;
        }
        return JSONObject.parseObject(normalized);
    }

    private String valueFromContext(Map<String, Object> context, String key, String defaultValue) {
        Object value = context.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private int intFromContext(Map<String, Object> context, String key, int defaultValue) {
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static boolean hasText(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface JsonTransport {
        String post(String url,
                    String body,
                    String apiKey,
                    int timeoutSeconds) throws Exception;
    }
}
