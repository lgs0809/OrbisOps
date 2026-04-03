package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsModelDeadlineHttpRequestTest {
    @Test
    void nativeReadTimeoutCanRetryTheSameModelWithinTheOverallBudget() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        var requests = new AtomicInteger();
        List<String> bodies = new CopyOnWriteArrayList<>();
        server.setExecutor(executor);
        server.createContext("/model", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            if (requests.incrementAndGet() == 1) {
                try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            byte[] response = "{\"model\":\"gpt-5.6-luna\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            RestClient client = OpsModelHttpClientFactory.restClientBuilder(1, 1).build();
            var events = new CopyOnWriteArrayList<OpsRuntimeEvent>();
            var retry = new OpsModelRequestRetry("gpt-5.6-luna", 5000, events::add);
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/model";
            retry.begin();
            RuntimeException timeout = assertThrows(RuntimeException.class,
                    () -> client.post().uri(uri).body("{\"model\":\"gpt-5.6-luna\"}").retrieve().body(String.class));
            assertFalse(OpsModelRequestRetry.interrupted(timeout));
            assertEquals("MODEL_PROVIDER_UNAVAILABLE", OpsModelProviderFailureClassifier.classify(timeout).orElseThrow().code());
            long delay = retry.retryDelay(timeout, false);
            assertTrue(delay >= 400 && delay <= 600);
            Thread.sleep(delay);
            retry.begin();
            String output = client.post().uri(uri).body("{\"model\":\"gpt-5.6-luna\"}").retrieve().body(String.class);
            retry.verified(com.alibaba.fastjson.JSON.parseObject(output).getString("model"));
            assertEquals(2, requests.get());
            assertEquals(List.of("{\"model\":\"gpt-5.6-luna\"}", "{\"model\":\"gpt-5.6-luna\"}"), bodies);
            assertTrue(events.stream().anyMatch(event -> "MODEL_CALL_RETRYING".equals(event.getEventType())));
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void elapsedNativeTimerCancellationBecomesTimeoutWithoutAnInterruptedCause() throws Exception {
        var delegate = mock(ClientHttpRequest.class);
        var cancelled = new CancellationException("native timer cancelled future");
        when(delegate.execute()).thenAnswer(invocation -> { Thread.sleep(25); throw cancelled; });
        var timeout = assertThrows(HttpTimeoutException.class,
                () -> new OpsModelDeadlineHttpRequest(delegate, 10).execute());
        assertEquals(1, timeout.getSuppressed().length);
        assertSame(cancelled, timeout.getSuppressed()[0]);
        assertFalse(OpsModelRequestRetry.interrupted(timeout));
    }

    @Test
    void earlyCallerCancellationAndThreadInterruptionNeverBecomeRetryable() throws Exception {
        var delegate = mock(ClientHttpRequest.class);
        var cancelled = new CancellationException("caller cancelled");
        when(delegate.execute()).thenThrow(cancelled);
        assertSame(cancelled, assertThrows(CancellationException.class,
                () -> new OpsModelDeadlineHttpRequest(delegate, 1000).execute()));
        var retry = new OpsModelRequestRetry("gpt-5.6-luna", 5000, null);
        retry.begin();
        assertEquals(-1, retry.retryDelay(cancelled, false));
        Thread.currentThread().interrupt();
        try {
            assertSame(cancelled, assertThrows(CancellationException.class,
                    () -> new OpsModelDeadlineHttpRequest(delegate, 0).execute()));
        } finally { Thread.interrupted(); }
    }

    @Test
    void nodeDeadlinePreventsAnotherPhysicalRequestAfterTimeout() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        var requests = new AtomicInteger();
        server.setExecutor(executor);
        server.createContext("/model", exchange -> {
            requests.incrementAndGet();
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            OpsNodeDeadlineContext.withDeadline(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300), () -> {
                var retry = new OpsModelRequestRetry("gpt-5.6-terra", 5000, null);
                retry.begin();
                var client = OpsModelHttpClientFactory.restClientBuilder(1, 5).build();
                var error = assertThrows(RuntimeException.class, () -> client.get()
                        .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/model").retrieve().body(String.class));
                assertEquals(-1, retry.retryDelay(error, false));
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(retry.remainingMillis() + 5));
                assertThrows(IllegalStateException.class, retry::begin);
                return null;
            });
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
