package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelSyncFetchResult;
import cn.lgs.orbisops.application.config.AiClientModelSyncTarget;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class OpsAiClientModelSyncHttpProtocolAdapterTest {

    @Test
    void resolvesModelsEndpointWithoutDuplicateSuffix() {
        OpsAiClientModelSyncHttpProtocolAdapter adapter = new OpsAiClientModelSyncHttpProtocolAdapter();

        assertEquals(
                "https://provider.example/v1/models",
                adapter.resolveEndpoint(new AiClientModelSyncTarget(
                        "provider", " https://provider.example/v1/// ", "")));
        assertEquals(
                "https://provider.example/v1/models",
                adapter.resolveEndpoint(new AiClientModelSyncTarget(
                        "provider", "https://provider.example/v1/models", "")));
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.resolveEndpoint(new AiClientModelSyncTarget("provider", " ", "")));
        assertEquals("Provider Base URL 为空", error.getMessage());
    }

    @Test
    void sendsBearerAndAcceptHeadersThenParsesTrimmedDistinctModelIds() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] body = """
                    {"data":[
                      {"id":" gpt-main "},
                      {"id":"gpt-main"},
                      {"id":"text-embedding-3-small"},
                      {"id":"  "},
                      {}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpsAiClientModelSyncHttpProtocolAdapter adapter = new OpsAiClientModelSyncHttpProtocolAdapter();
            AiClientModelSyncTarget target = new AiClientModelSyncTarget(
                    "openai-main",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "unit-test-api-key");
            String endpoint = adapter.resolveEndpoint(target);

            AiClientModelSyncFetchResult result = adapter.fetch(target, endpoint);

            assertEquals(endpoint, result.endpoint());
            assertEquals(200, result.httpStatus());
            assertEquals(List.of("gpt-main", "text-embedding-3-small"), result.modelIds());
            assertEquals("Bearer unit-test-api-key", authorization.get());
            assertEquals("application/json", accept.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessResponseIncludesStatusAndTruncatesBodyToThreeHundredCharacters() throws Exception {
        String bodyText = "x".repeat(350) + "TAIL-MUST-NOT-APPEAR";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> {
            byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpsAiClientModelSyncHttpProtocolAdapter adapter = new OpsAiClientModelSyncHttpProtocolAdapter();
            AiClientModelSyncTarget target = new AiClientModelSyncTarget(
                    "provider",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/models",
                    "");

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> adapter.fetch(target, adapter.resolveEndpoint(target)));

            assertTrue(error.getMessage().startsWith("Provider /models HTTP 503: "));
            assertTrue(error.getMessage().contains("x".repeat(300)));
            assertFalse(error.getMessage().contains("TAIL-MUST-NOT-APPEAR"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void ordinaryIoFailureDoesNotSetThreadInterruptFlag() throws Exception {
        Thread.interrupted();
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("io-down"))
                .when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        OpsAiClientModelSyncHttpProtocolAdapter adapter = new OpsAiClientModelSyncHttpProtocolAdapter(client);
        AiClientModelSyncTarget target = new AiClientModelSyncTarget(
                "provider", "https://provider.example/v1", "");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> adapter.fetch(target, adapter.resolveEndpoint(target)));

        assertEquals("io-down", error.getMessage());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void interruptedFailureRestoresThreadInterruptFlag() throws Exception {
        Thread.interrupted();
        HttpClient client = mock(HttpClient.class);
        doThrow(new InterruptedException("interrupted"))
                .when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        OpsAiClientModelSyncHttpProtocolAdapter adapter = new OpsAiClientModelSyncHttpProtocolAdapter(client);
        AiClientModelSyncTarget target = new AiClientModelSyncTarget(
                "provider", "https://provider.example/v1", "");
        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> adapter.fetch(target, adapter.resolveEndpoint(target)));

            assertEquals("interrupted", error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
