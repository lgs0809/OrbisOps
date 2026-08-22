package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiHealthProbeOutcome;
import cn.lgs.orbisops.application.config.AiClientApiHealthTarget;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAiClientApiHealthHttpProbeAdapterTest {

    @Test
    void probesOpenAiCompatibleModelsEndpointAndPassesBearerCredential() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            OpsAiClientApiHealthHttpProbeAdapter adapter =
                    new OpsAiClientApiHealthHttpProbeAdapter(HttpClient.newHttpClient());

            AiClientApiHealthProbeOutcome outcome = adapter.probe(
                    new AiClientApiHealthTarget("local-provider", baseUrl, "v1/chat/completions", "unit-test-api-key"));

            assertEquals("SUCCESS", outcome.status());
            assertEquals(200, outcome.httpStatus());
            assertEquals(baseUrl + "/v1/models", outcome.endpoint());
            assertEquals("Bearer unit-test-api-key", authorization.get());
            assertTrue(outcome.latencyMs() >= 0L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emptyBaseUrlReturnsTypedFailureInsteadOfThrowing() {
        OpsAiClientApiHealthHttpProbeAdapter adapter =
                new OpsAiClientApiHealthHttpProbeAdapter(HttpClient.newHttpClient());

        AiClientApiHealthProbeOutcome outcome = adapter.probe(
                new AiClientApiHealthTarget("broken-provider", "", "v1/chat/completions", ""));

        assertEquals("FAILED", outcome.status());
        assertEquals("Provider Base URL 为空，无法检测", outcome.errorMessage());
        assertEquals("", outcome.endpoint());
    }
}
