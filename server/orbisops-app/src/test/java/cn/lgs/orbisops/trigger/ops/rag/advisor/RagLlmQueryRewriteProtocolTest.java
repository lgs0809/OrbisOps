package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagLlmQueryRewriteProtocolTest {

    @Test
    void shouldBuildCompatibleRequestAndMergeFencedJsonQueries() {
        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicInteger timeout = new AtomicInteger();
        String longQuery = "X".repeat(300);
        RagLlmQueryRewriteProtocol protocol = new RagLlmQueryRewriteProtocol(
                (requestUrl, requestBody, requestApiKey, timeoutSeconds) -> {
                    url.set(requestUrl);
                    body.set(requestBody);
                    apiKey.set(requestApiKey);
                    timeout.set(timeoutSeconds);
                    JSONObject content = new JSONObject(true);
                    content.put("queries", List.of(
                            "original query",
                            "seed query",
                            "  new evidence query  ",
                            longQuery));
                    JSONObject message = new JSONObject(true);
                    message.put("content", "```json\n" + content.toJSONString() + "\n```");
                    JSONObject choice = new JSONObject(true);
                    choice.put("message", message);
                    JSONObject response = new JSONObject(true);
                    response.put("choices", List.of(choice));
                    return response.toJSONString();
                });
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .llmQueryRewriteBaseUrl("http://rewrite.local/")
                .llmQueryRewriteApiKey("configured-key")
                .llmQueryRewritePath("/v1/chat/completions")
                .llmQueryRewriteModel("configured-model")
                .llmQueryRewriteMaxQueries(8)
                .llmQueryRewriteTimeoutSeconds(2)
                .build();
        Map<String, Object> context = Map.of(
                "qa_llm_query_rewrite_api_key", "context-key",
                "qa_llm_query_rewrite_model", "context-model",
                "qa_llm_query_rewrite_timeout_seconds", 120,
                "qa_query_rewrite_max_queries", 4,
                "qa_filter_expression", "knowledge == 'demo-ops'");

        RagLlmQueryRewriteResult result = protocol.rewrite(
                "original query",
                context,
                settings,
                List.of("original query", "seed query"),
                true,
                "knowledge == 'fallback'");

        assertTrue(result.generated());
        assertFalse(result.degraded());
        assertNull(result.degradationError());
        assertNull(result.cause());
        assertEquals(4, result.queries().size());
        assertEquals("original query", result.queries().get(0));
        assertEquals("seed query", result.queries().get(1));
        assertEquals("new evidence query", result.queries().get(2));
        assertEquals("X".repeat(280) + "...", result.queries().get(3));

        assertEquals("http://rewrite.local/v1/chat/completions", url.get());
        assertEquals("context-key", apiKey.get());
        assertEquals(90, timeout.get());

        JSONObject request = JSONObject.parseObject(body.get());
        assertEquals("context-model", request.getString("model"));
        assertEquals(0, request.getIntValue("temperature"));
        assertEquals(600, request.getIntValue("max_completion_tokens"));
        assertEquals("json_object", request.getJSONObject("response_format").getString("type"));
        JSONArray messages = request.getJSONArray("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertTrue(messages.getJSONObject(0).getString("content").contains("queries 最多 4 条"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        JSONObject payload = JSONObject.parseObject(messages.getJSONObject(1).getString("content"));
        assertEquals("original query", payload.getString("originalQuery"));
        assertEquals(List.of("original query", "seed query"), payload.getJSONArray("seedQueries").toJavaList(String.class));
        assertEquals("knowledge == 'demo-ops'", payload.getString("knowledgeFilter"));
        assertTrue(payload.getBooleanValue("lowRecallRetry"));
        assertTrue(payload.getString("retrievalGoal").contains("故障案例"));
    }

    @Test
    void shouldReturnCompatibleConfigurationDegradationWithoutCallingTransport() {
        AtomicInteger calls = new AtomicInteger();
        RagLlmQueryRewriteProtocol protocol = new RagLlmQueryRewriteProtocol(
                (url, body, apiKey, timeout) -> {
                    calls.incrementAndGet();
                    return "{}";
                });
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .llmQueryRewriteBaseUrl("http://rewrite.local")
                .llmQueryRewritePath("v1/chat/completions")
                .llmQueryRewriteModel("model")
                .build();
        List<String> seeds = new ArrayList<>(List.of("original", "seed"));

        RagLlmQueryRewriteResult result = protocol.rewrite(
                "original",
                Map.of(),
                settings,
                seeds,
                false,
                null);
        seeds.add("mutated");

        assertFalse(result.generated());
        assertTrue(result.degraded());
        assertEquals("RAG LLM query rewrite 配置不完整，无法调用模型", result.degradationError());
        assertNull(result.cause());
        assertEquals(List.of("original", "seed"), result.queries());
        assertEquals(0, calls.get());
    }

    @Test
    void shouldPreserveChoicesContentAndQueriesValidationErrors() {
        RagRetrievalSettings settings = configuredSettings();
        List<ProtocolCase> cases = List.of(
                new ProtocolCase("{}", "RAG LLM query rewrite 未返回 choices"),
                new ProtocolCase("{\"choices\":[]}", "RAG LLM query rewrite 未返回 choices"),
                new ProtocolCase("{\"choices\":[{\"message\":{}}]}", "RAG LLM query rewrite 未返回 content"),
                new ProtocolCase("{\"choices\":[{\"message\":{\"content\":\"   \"}}]}", "RAG LLM query rewrite 未返回 content"),
                new ProtocolCase("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}", "RAG LLM query rewrite 未返回 queries"),
                new ProtocolCase("{\"choices\":[{\"message\":{\"content\":\"[]\"}}]}", "RAG LLM query rewrite 未返回 queries"));

        for (ProtocolCase protocolCase : cases) {
            RagLlmQueryRewriteProtocol protocol = new RagLlmQueryRewriteProtocol(
                    (url, body, apiKey, timeout) -> protocolCase.response());

            RagLlmQueryRewriteResult result = protocol.rewrite(
                    "original",
                    Map.of(),
                    settings,
                    List.of("original", "seed"),
                    false,
                    null);

            assertFalse(result.generated());
            assertTrue(result.degraded());
            assertEquals(protocolCase.expectedError(), result.degradationError());
            assertNull(result.cause());
            assertEquals(List.of("original", "seed"), result.queries());
        }
    }

    @Test
    void shouldReturnProtocolFailureWithOriginalCause() {
        IllegalStateException failure = new IllegalStateException("transport unavailable");
        RagLlmQueryRewriteProtocol protocol = new RagLlmQueryRewriteProtocol(
                (url, body, apiKey, timeout) -> {
                    throw failure;
                });

        RagLlmQueryRewriteResult result = protocol.rewrite(
                "original",
                Map.of(),
                configuredSettings(),
                List.of("original", "seed"),
                false,
                null);

        assertFalse(result.generated());
        assertTrue(result.degraded());
        assertEquals("RAG LLM query rewrite 失败：transport unavailable", result.degradationError());
        assertSame(failure, result.cause());
        assertEquals(List.of("original", "seed"), result.queries());
    }

    @Test
    void shouldFallbackMalformedNumericOverridesAndClampLowerBounds() {
        AtomicInteger timeout = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        RagLlmQueryRewriteProtocol protocol = new RagLlmQueryRewriteProtocol(
                (url, requestBody, apiKey, timeoutSeconds) -> {
                    timeout.set(timeoutSeconds);
                    body.set(requestBody);
                    return "{\"choices\":[{\"message\":{\"content\":\"{\\\"queries\\\":[\\\"new query\\\"]}\"}}]}";
                });
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .llmQueryRewriteBaseUrl("http://rewrite.local")
                .llmQueryRewriteApiKey("key")
                .llmQueryRewritePath("v1/chat/completions")
                .llmQueryRewriteModel("model")
                .llmQueryRewriteMaxQueries(3)
                .llmQueryRewriteTimeoutSeconds(5)
                .build();

        RagLlmQueryRewriteResult malformed = protocol.rewrite(
                "original",
                Map.of(
                        "qa_query_rewrite_max_queries", "invalid",
                        "qa_llm_query_rewrite_timeout_seconds", "invalid"),
                settings,
                List.of("original", "seed"),
                false,
                "fallback-filter");
        assertTrue(malformed.generated());
        assertTrue(JSONObject.parseObject(body.get()).getJSONArray("messages")
                .getJSONObject(0).getString("content").contains("最多 3 条"));
        assertEquals(5, timeout.get());

        RagLlmQueryRewriteResult lowerClamp = protocol.rewrite(
                "original",
                Map.of(
                        "qa_query_rewrite_max_queries", 0,
                        "qa_llm_query_rewrite_timeout_seconds", 0),
                settings,
                List.of("original", "seed"),
                false,
                "fallback-filter");
        assertTrue(lowerClamp.generated());
        assertEquals(List.of("original"), lowerClamp.queries());
        assertEquals(1, timeout.get());
    }

    @Test
    void resultMustExposeDefensiveQueriesAndDegradationState() {
        Exception cause = new IllegalArgumentException("bad");
        List<String> values = new ArrayList<>(List.of("query"));
        RagLlmQueryRewriteResult result = RagLlmQueryRewriteResult.degraded(values, "error", cause);
        values.add("mutated");

        assertTrue(result.degraded());
        assertFalse(result.generated());
        assertEquals(List.of("query"), result.queries());
        assertEquals("error", result.degradationError());
        assertSame(cause, result.cause());
        assertNotNull(result.queries());
    }

    private RagRetrievalSettings configuredSettings() {
        return RagRetrievalSettings.builder()
                .llmQueryRewriteBaseUrl("http://rewrite.local")
                .llmQueryRewriteApiKey("key")
                .llmQueryRewritePath("v1/chat/completions")
                .llmQueryRewriteModel("model")
                .llmQueryRewriteMaxQueries(4)
                .llmQueryRewriteTimeoutSeconds(2)
                .build();
    }

    private record ProtocolCase(String response, String expectedError) {
    }
}
