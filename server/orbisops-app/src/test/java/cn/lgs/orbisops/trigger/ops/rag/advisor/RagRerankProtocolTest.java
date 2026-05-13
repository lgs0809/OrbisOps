package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRerankProtocolTest {

    @Test
    void shouldBuildVoyagePayloadNormalizeEndpointAndApplyScores() {
        AtomicReference<URI> uri = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<Integer> timeout = new AtomicReference<>();
        RagRerankProtocol protocol = protocol("secret", (requestUri, requestBody, key, timeoutSeconds) -> {
            uri.set(requestUri);
            body.set(requestBody);
            apiKey.set(key);
            timeout.set(timeoutSeconds);
            return new RagRerankProtocol.HttpResult(200, """
                    {"data":[
                      {"index":2,"relevance_score":0.91},
                      {"index":0,"relevance_score":0.44},
                      {"index":99,"relevance_score":1.0},
                      {"index":null,"relevance_score":0.8}
                    ]}
                    """);
        });
        List<Document> candidates = candidates();

        List<Document> reranked = protocol.rerank(
                "锁单失败",
                candidates,
                plan("voyage", "https://rerank.example.com/", "/v1/rerank", 2, 8),
                new HashMap<>());

        assertEquals(URI.create("https://rerank.example.com/v1/rerank"), uri.get());
        assertEquals("secret", apiKey.get());
        assertEquals(45, timeout.get());
        JSONObject payload = JSONObject.parseObject(body.get());
        assertEquals("rerank-model", payload.getString("model"));
        assertEquals("锁单失败", payload.getString("query"));
        assertEquals(2, payload.getIntValue("top_k"));
        assertTrue(payload.getBooleanValue("truncation"));
        assertFalse(payload.getBooleanValue("return_documents"));
        JSONArray documents = payload.getJSONArray("documents");
        assertEquals(3, documents.size());
        assertEquals("first do...", documents.getString(0));

        assertEquals(List.of("third", "first"), reranked.stream().map(Document::getId).toList());
        assertEquals("voyage", reranked.get(0).getMetadata().get("rerank_provider"));
        assertEquals(true, reranked.get(0).getMetadata().get("reranked"));
        assertEquals(0.91d,
                ((Number) reranked.get(0).getMetadata().get("rerank_score")).doubleValue(),
                0.000000000001d);
        assertFalse(candidates.get(0).getMetadata().containsKey("reranked"));
    }

    @Test
    void shouldAcceptVoyageResultsAliasAndBuildCoherePayload() {
        AtomicReference<JSONObject> voyageBody = new AtomicReference<>();
        RagRerankProtocol voyage = protocol("voyage-key", (uri, body, key, timeout) -> {
            voyageBody.set(JSONObject.parseObject(body));
            return new RagRerankProtocol.HttpResult(
                    200,
                    "{\"results\":[{\"index\":1,\"relevance_score\":0.7}]}" );
        });
        AtomicReference<URI> cohereUri = new AtomicReference<>();
        AtomicReference<JSONObject> cohereBody = new AtomicReference<>();
        RagRerankProtocol cohere = protocol("cohere-key", (uri, body, key, timeout) -> {
            cohereUri.set(uri);
            cohereBody.set(JSONObject.parseObject(body));
            return new RagRerankProtocol.HttpResult(
                    200,
                    "{\"results\":[{\"index\":0,\"relevance_score\":0.8}]}" );
        });

        List<Document> voyageResult = voyage.rerank(
                "query",
                candidates(),
                plan("voyage", "https://voyage.example.com", "rerank", 1, 1200),
                Map.of());
        List<Document> cohereResult = cohere.rerank(
                "query",
                candidates(),
                plan("cohere", "https://cohere.example.com/", "/v2/rerank", 1, 1200),
                Map.of());

        assertEquals("second", voyageResult.get(0).getId());
        assertEquals(1, voyageBody.get().getIntValue("top_k"));
        assertTrue(voyageBody.get().containsKey("truncation"));
        assertEquals(URI.create("https://cohere.example.com/v2/rerank"), cohereUri.get());
        assertEquals(1, cohereBody.get().getIntValue("top_n"));
        assertFalse(cohereBody.get().containsKey("top_k"));
        assertFalse(cohereBody.get().containsKey("truncation"));
        assertEquals("first", cohereResult.get(0).getId());
        assertEquals("cohere", cohereResult.get(0).getMetadata().get("rerank_provider"));
    }

    @Test
    void shouldKeepUnscoredCandidatesBehindScoredCandidatesInOriginalOrder() {
        RagRerankProtocol protocol = protocol("secret", (uri, body, key, timeout) ->
                new RagRerankProtocol.HttpResult(
                        200,
                        "{\"results\":[{\"index\":1,\"relevance_score\":0.8}]}"));

        List<Document> reranked = protocol.rerank(
                "query",
                candidates(),
                plan("cohere", "https://cohere.example.com", "v1/rerank", 3, 1200),
                Map.of());

        assertEquals(List.of("second", "first", "third"),
                reranked.stream().map(Document::getId).toList());
        assertEquals(true, reranked.get(1).getMetadata().get("reranked"));
        assertEquals("cohere", reranked.get(1).getMetadata().get("rerank_provider"));
        assertFalse(reranked.get(1).getMetadata().containsKey("rerank_score"));
        assertFalse(reranked.get(2).getMetadata().containsKey("rerank_score"));
    }

    @Test
    void shouldReturnOriginalCandidatesForDisabledNoneSingleEmptyOrInvalidResults() {
        AtomicInteger calls = new AtomicInteger();
        RagRerankProtocol protocol = protocol("secret", (uri, body, key, timeout) -> {
            calls.incrementAndGet();
            return new RagRerankProtocol.HttpResult(
                    200,
                    "{\"results\":[{\"index\":99,\"relevance_score\":0.8},{\"index\":0}]}" );
        });
        List<Document> candidates = candidates();

        assertSame(candidates, protocol.rerank(
                "query", candidates, disabledPlan(), Map.of()));
        assertSame(candidates, protocol.rerank(
                "query", candidates, plan("none", "https://example.com", "rerank", 2, 1200), Map.of()));
        List<Document> single = candidates.subList(0, 1);
        assertSame(single, protocol.rerank(
                "query", single, plan("cohere", "https://example.com", "rerank", 1, 1200), Map.of()));
        assertEquals(0, calls.get());

        List<Document> invalid = protocol.rerank(
                "query", candidates, plan("cohere", "https://example.com", "rerank", 2, 1200), Map.of());
        assertSame(candidates, invalid);
        assertEquals(1, calls.get());

        RagRerankProtocol empty = protocol("secret", (uri, body, key, timeout) ->
                new RagRerankProtocol.HttpResult(200, "{\"results\":[]}"));
        assertSame(candidates, empty.rerank(
                "query", candidates, plan("cohere", "https://example.com", "rerank", 2, 1200), Map.of()));
    }

    @Test
    void shouldMarkIncompleteConfigurationAndHonorStrictFailure() {
        RagRerankProtocol protocol = protocol("", (uri, body, key, timeout) -> {
            throw new AssertionError("transport must not be called");
        });
        List<Document> candidates = candidates();
        Map<String, Object> degradedContext = new HashMap<>();

        List<Document> degraded = protocol.rerank(
                "query",
                candidates,
                plan("cohere", "https://example.com", "rerank", 2, 1200),
                degradedContext);

        assertSame(candidates, degraded);
        assertEquals(true, degradedContext.get("qa_rerank_degraded"));
        assertTrue(String.valueOf(degradedContext.get("qa_rerank_error")).contains("配置不完整"));

        Map<String, Object> strictContext = new HashMap<>();
        strictContext.put("qa_rerank_fail_on_degradation", true);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> protocol.rerank(
                "query",
                candidates,
                plan("cohere", "https://example.com", "rerank", 2, 1200),
                strictContext));
        assertTrue(failure.getMessage().contains("配置不完整"));
        assertEquals(true, strictContext.get("qa_rerank_degraded"));
    }

    @Test
    void shouldDegradeHttpAndJsonFailuresAndPreserveCauseInStrictMode() {
        List<Document> candidates = candidates();
        RagRetrievalPlan plan = plan("cohere", "https://example.com", "rerank", 2, 1200);
        RagRerankProtocol httpFailure = protocol("secret", (uri, body, key, timeout) ->
                new RagRerankProtocol.HttpResult(503, "service unavailable"));
        Map<String, Object> context = new HashMap<>();

        assertSame(candidates, httpFailure.rerank("query", candidates, plan, context));
        assertEquals(true, context.get("qa_rerank_degraded"));
        assertTrue(String.valueOf(context.get("qa_rerank_error")).contains("Rerank HTTP 503"));

        RagRerankProtocol malformedJson = protocol("secret", (uri, body, key, timeout) ->
                new RagRerankProtocol.HttpResult(200, "not-json"));
        Map<String, Object> strictContext = new HashMap<>();
        strictContext.put("qa_rerank_fail_on_degradation", true);
        IllegalStateException strictFailure = assertThrows(IllegalStateException.class, () -> malformedJson.rerank(
                "query", candidates, plan, strictContext));
        assertTrue(strictFailure.getMessage().startsWith("RAG rerank 失败："));
        assertTrue(strictFailure.getCause() != null);
        assertEquals(true, strictContext.get("qa_rerank_degraded"));
    }

    @Test
    void shouldReturnCandidatesForUnknownProviderWithoutCallingTransport() {
        AtomicInteger calls = new AtomicInteger();
        RagRerankProtocol protocol = protocol("secret", (uri, body, key, timeout) -> {
            calls.incrementAndGet();
            return new RagRerankProtocol.HttpResult(200, "{}");
        });
        List<Document> candidates = candidates();

        assertSame(candidates, protocol.rerank(
                "query",
                candidates,
                plan("custom", "https://example.com", "rerank", 2, 1200),
                Map.of()));
        assertEquals(0, calls.get());
    }

    private RagRerankProtocol protocol(String apiKey,
                                       RagRerankProtocol.HttpTransport transport) {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .rerankApiKey(apiKey)
                .build();
        return new RagRerankProtocol(settings, transport);
    }

    private List<Document> candidates() {
        return List.of(
                new Document("first", "first document content", Map.of("source", "first.md")),
                new Document("second", "second document content", Map.of("source", "second.md")),
                new Document("third", "third document content", Map.of("source", "third.md")));
    }

    private RagRetrievalPlan disabledPlan() {
        return new RagRetrievalPlan(
                "hybrid",
                5,
                5,
                3,
                12000,
                false,
                "none",
                null,
                null,
                null,
                5,
                3,
                1200);
    }

    private RagRetrievalPlan plan(String provider,
                                  String baseUrl,
                                  String path,
                                  int topN,
                                  int maxDocumentChars) {
        return new RagRetrievalPlan(
                "hybrid",
                5,
                5,
                3,
                12000,
                true,
                provider,
                baseUrl,
                path,
                "rerank-model",
                5,
                topN,
                maxDocumentChars);
    }
}
