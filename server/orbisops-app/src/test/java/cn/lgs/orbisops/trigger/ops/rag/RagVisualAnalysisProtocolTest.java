package cn.lgs.orbisops.trigger.ops.rag;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualAnalysisProtocolTest {

    @Test
    void requestBodyMustPreserveOpenAiCompatibleSchemaAndImageContract() {
        RagVisualAnalysisSettings settings = settings(
                "max_tokens", "json_object", 800, 0);
        RagVisualAnalysisProtocol protocol = new RagVisualAnalysisProtocol(
                settings,
                request -> new RagVisualAnalysisProtocol.TransportResponse(200, successBody("{}")),
                attempt -> { });

        JSONObject body = protocol.requestBody(new byte[]{1, 2, 3}, "image/png");

        assertEquals("visual-model", body.getString("model"));
        assertEquals(0, body.getIntValue("temperature"));
        assertEquals(800, body.getIntValue("max_tokens"));
        assertFalse(body.containsKey("max_completion_tokens"));
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"));
        JSONArray messages = body.getJSONArray("messages");
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        JSONObject imageUrl = messages.getJSONObject(1)
                .getJSONArray("content")
                .getJSONObject(1)
                .getJSONObject("image_url");
        assertTrue(imageUrl.getString("url").startsWith("data:image/png;base64,"));
        assertEquals("high", imageUrl.getString("detail"));
    }

    @Test
    void successfulAndRefusedResponsesMustMapStableEnvelope() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RagVisualAnalysisProtocol protocol = new RagVisualAnalysisProtocol(
                settings("max_completion_tokens", "json_schema", 1200, 0),
                request -> calls.getAndIncrement() == 0
                        ? new RagVisualAnalysisProtocol.TransportResponse(
                                200,
                                successBody("{\"title\":\"diagram\"}"))
                        : new RagVisualAnalysisProtocol.TransportResponse(
                                200,
                                refusalBody("policy-block")),
                attempt -> { });

        RagVisualAnalysisProtocol.AnalysisResponse success = protocol.analyze(
                new byte[]{1}, "image/png");
        RagVisualAnalysisProtocol.AnalysisResponse refusal = protocol.analyze(
                new byte[]{2}, "image/jpeg");

        assertFalse(success.refused());
        assertEquals("{\"title\":\"diagram\"}", success.content());
        assertTrue(refusal.refused());
        assertEquals("policy-block", refusal.refusal());
        assertEquals("", refusal.content());
    }

    @Test
    void transportMustReceiveEndpointAuthorizationAndTimeout() throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        RagVisualAnalysisProtocol protocol = new RagVisualAnalysisProtocol(
                settings("both", "none", 900, 0),
                request -> {
                    requests.add(request);
                    return new RagVisualAnalysisProtocol.TransportResponse(
                            200,
                            successBody("{}"));
                },
                attempt -> { });

        protocol.analyze(new byte[]{1}, "image/png");

        HttpRequest request = requests.get(0);
        assertEquals("https://visual.example.com/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer visual-test-api-key", request.headers()
                .firstValue("Authorization").orElseThrow());
        assertEquals("application/json", request.headers()
                .firstValue("Content-Type").orElseThrow());
        assertEquals(30L, request.timeout().orElseThrow().toSeconds());
    }

    @Test
    void retryableFailureMustRetryAndInvokeDelayBeforeSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> delayedAttempts = new ArrayList<>();
        RagVisualAnalysisProtocol protocol = new RagVisualAnalysisProtocol(
                settings("max_completion_tokens", "json_schema", 1200, 1),
                request -> calls.getAndIncrement() == 0
                        ? new RagVisualAnalysisProtocol.TransportResponse(500, "temporary")
                        : new RagVisualAnalysisProtocol.TransportResponse(
                                200,
                                successBody("{}")),
                delayedAttempts::add);

        RagVisualAnalysisProtocol.AnalysisResponse response = protocol.analyze(
                new byte[]{1}, "image/png");

        assertFalse(response.refused());
        assertEquals(2, calls.get());
        assertEquals(List.of(1), delayedAttempts);
    }

    @Test
    void finalTransportFailureMustPropagateProtocolError() {
        RagVisualAnalysisProtocol protocol = new RagVisualAnalysisProtocol(
                settings("max_completion_tokens", "json_schema", 1200, 0),
                request -> new RagVisualAnalysisProtocol.TransportResponse(429, "busy"),
                attempt -> { });

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> protocol.analyze(new byte[]{1}, "image/png"));

        assertTrue(error.getMessage().contains("Visual parse HTTP 429 busy"));
    }

    private RagVisualAnalysisSettings settings(
            String tokenField,
            String responseFormat,
            int tokenLimit,
            int retries) {
        return new RagVisualAnalysisSettings(
                true,
                false,
                "openai",
                "https://visual.example.com/",
                "visual-test-api-key",
                "/v1/chat/completions",
                "visual-model",
                "high",
                30,
                tokenLimit,
                tokenField,
                responseFormat,
                retries,
                3,
                4_194_304L,
                144);
    }

    private String successBody(String content) {
        JSONObject message = new JSONObject(true);
        message.put("content", content);
        JSONObject choice = new JSONObject(true);
        choice.put("message", message);
        JSONArray choices = new JSONArray();
        choices.add(choice);
        JSONObject response = new JSONObject(true);
        response.put("choices", choices);
        return response.toJSONString();
    }

    private String refusalBody(String refusal) {
        JSONObject message = new JSONObject(true);
        message.put("refusal", refusal);
        JSONObject choice = new JSONObject(true);
        choice.put("message", message);
        JSONArray choices = new JSONArray();
        choices.add(choice);
        JSONObject response = new JSONObject(true);
        response.put("choices", choices);
        return response.toJSONString();
    }
}
