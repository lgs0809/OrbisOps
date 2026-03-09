package cn.lgs.orbisops.trigger.ops.rag;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalEmbeddingProtocolTest {

    @Test
    void textEmbeddingMustProjectRequestAndParseEmbeddingsResponse() throws Exception {
        AtomicReference<URI> uri = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicInteger timeoutSeconds = new AtomicInteger();
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings(2048, 1, 4),
                (requestUri, body, credential, timeout) -> {
                    uri.set(requestUri);
                    requestBody.set(body);
                    apiKey.set(credential);
                    timeoutSeconds.set(timeout);
                    return new RagMultimodalEmbeddingProtocol.HttpResult(
                            200, "{\"embeddings\":[[0.125,0.5]]}");
                },
                attempt -> {
                });

        List<Double> embedding = protocol.embedText("abcdef", "query");

        assertEquals(List.of(0.125d, 0.5d), embedding);
        assertEquals(URI.create("http://localhost/v1/embed"), uri.get());
        assertEquals("test-credential", apiKey.get());
        assertEquals(30, timeoutSeconds.get());

        JSONObject body = JSONObject.parseObject(requestBody.get());
        assertEquals("model", body.getString("model"));
        assertEquals("query", body.getString("input_type"));
        assertTrue(body.getBooleanValue("truncation"));
        assertEquals(2048, body.getIntValue("output_dimension"));
        JSONArray content = body.getJSONArray("inputs")
                .getJSONObject(0)
                .getJSONArray("content");
        assertEquals(1, content.size());
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("abcd...", content.getJSONObject(0).getString("text"));
    }

    @Test
    void imageEmbeddingMustProjectDataUriAndParseDataResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings(1024, 0, 3000),
                (uri, body, apiKey, timeout) -> {
                    requestBody.set(body);
                    return new RagMultimodalEmbeddingProtocol.HttpResult(
                            200, "{\"data\":[{\"embedding\":[1,2.5]}]}");
                },
                attempt -> {
                });

        List<Double> embedding = protocol.embedImage(
                "caption", new byte[]{1, 2, 3}, "image/png", " ");

        assertEquals(List.of(1d, 2.5d), embedding);
        JSONObject body = JSONObject.parseObject(requestBody.get());
        assertEquals("document", body.getString("input_type"));
        assertFalse(body.containsKey("output_dimension"));
        JSONArray content = body.getJSONArray("inputs")
                .getJSONObject(0)
                .getJSONArray("content");
        assertEquals(2, content.size());
        assertEquals("caption", content.getJSONObject(0).getString("text"));
        assertEquals("image_base64", content.getJSONObject(1).getString("type"));
        assertEquals("data:image/png;base64,AQID", content.getJSONObject(1).getString("image_base64"));
    }

    @Test
    void retryableHttpStatusMustRetryThroughInjectedDelay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> delayedAttempts = new ArrayList<>();
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings(2048, 2, 3000),
                (uri, body, apiKey, timeout) -> calls.incrementAndGet() == 1
                        ? new RagMultimodalEmbeddingProtocol.HttpResult(429, "busy")
                        : new RagMultimodalEmbeddingProtocol.HttpResult(200, "{\"embeddings\":[[0.75]]}"),
                delayedAttempts::add);

        List<Double> embedding = protocol.embedText("query", "query");

        assertEquals(List.of(0.75d), embedding);
        assertEquals(2, calls.get());
        assertEquals(List.of(1), delayedAttempts);
    }

    @Test
    void transportFailureMustRemainRetryableWithinConfiguredAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings(2048, 1, 3000),
                (uri, body, apiKey, timeout) -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IOException("temporary transport failure");
                    }
                    return new RagMultimodalEmbeddingProtocol.HttpResult(
                            200, "{\"embeddings\":[[0.25]]}");
                },
                attempt -> {
                });

        assertEquals(List.of(0.25d), protocol.embedText("query", "query"));
        assertEquals(2, calls.get());
    }

    @Test
    void invalidResponseMustFailWithStableProtocolError() {
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings(2048, 0, 3000),
                (uri, body, apiKey, timeout) -> new RagMultimodalEmbeddingProtocol.HttpResult(200, "{}"),
                attempt -> {
                });

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> protocol.embedText("query", "query"));

        assertEquals("Multimodal embedding response does not contain embeddings", exception.getMessage());
    }

    @Test
    void finalHttpFailureMustKeepStatusAndBoundedBody() {
        String responseBody = "x".repeat(220);
        RagMultimodalEmbeddingProtocol protocol = new RagMultimodalEmbeddingProtocol(
                settings(2048, 0, 3000),
                (uri, body, apiKey, timeout) -> new RagMultimodalEmbeddingProtocol.HttpResult(400, responseBody),
                attempt -> {
                });

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> protocol.embedText("query", "query"));

        assertTrue(exception.getMessage().startsWith("Multimodal embedding HTTP 400 "));
        assertTrue(exception.getMessage().endsWith("..."));
        assertFalse(exception.getMessage().contains("x".repeat(181)));
    }

    private RagMultimodalSettings settings(int dimension, int maxRetries, int maxTextChars) {
        return new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "test-credential",
                "v1/embed",
                "model",
                "table_name",
                dimension,
                true,
                false,
                true,
                true,
                3,
                144,
                20_971_520L,
                maxTextChars,
                8,
                30,
                maxRetries);
    }
}
