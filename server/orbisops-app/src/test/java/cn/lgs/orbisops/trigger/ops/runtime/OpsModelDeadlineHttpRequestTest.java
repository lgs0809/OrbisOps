package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.OpsRunControlContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpsModelDeadlineHttpRequestTest {
    @Test void trustedCancelClosesNativeConnectionWhileWaitingForHeaders() throws Exception {
        assertNativeTransportCancel(false);
    }

    @Test void trustedCancelClosesNativeConnectionWhileReadingBody() throws Exception {
        assertNativeTransportCancel(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelAtHeadersHandoffClosesBodyEvenWhenSpringNeverReceivesTheResponse() throws Exception {
        var registry = new OpsRunCancellationRegistry();
        var binding = new OpsRunControlContext.Binding("cancel-handoff", "project", Map.of(), registry);
        var waitingForHandoff = new CountDownLatch(1);
        var holdHandoff = new CountDownLatch(1);
        var body = mock(InputStream.class);
        var response = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(response.body()).thenReturn(body);
        var exchange = new CompletableFuture<HttpResponse<InputStream>>() {
            @Override
            public HttpResponse<InputStream> get() throws InterruptedException {
                // Headers are available, but the Spring thread can still be interrupted before it reads them.
                waitingForHandoff.countDown();
                holdHandoff.await();
                return response;
            }
        };
        exchange.complete(response);
        var client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers
                .<HttpResponse.BodyHandler<InputStream>>any())).thenReturn(exchange);
        var factory = OpsModelHttpClientFactory.requestFactory(client, 30);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var call = worker.submit(() -> OpsRunControlContext.withBinding(binding,
                    () -> assertThrows(OpsRunCanceledException.class, () -> factory.createRequest(
                            URI.create("http://127.0.0.1/model"), HttpMethod.GET).execute())));
            assertTrue(waitingForHandoff.await(4, TimeUnit.SECONDS));
            registry.markCanceled("cancel-handoff");
            call.get(4, TimeUnit.SECONDS);
            verify(body).close();
        } finally {
            holdHandoff.countDown();
            worker.shutdownNow();
        }
    }

    private void assertNativeTransportCancel(boolean headersFirst) throws Exception {
        var registry = new cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry();
        var control = new cn.lgs.orbisops.trigger.ops.OpsRunControlContext.Binding("cancel-run", "project", java.util.Map.of(), registry);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverWorkers = Executors.newCachedThreadPool(); var worker = Executors.newSingleThreadExecutor();
        var owner = Executors.newSingleThreadExecutor();
        var ready = new java.util.concurrent.CountDownLatch(1);
        var releaseHeaders = new java.util.concurrent.CountDownLatch(headersFirst ? 0 : 1);
        var disconnected = new java.util.concurrent.CountDownLatch(1);
        var workerEnded = new java.util.concurrent.CountDownLatch(1);
        var requests = new AtomicInteger();
        var events = new CopyOnWriteArrayList<OpsRuntimeEvent>();
        server.setExecutor(serverWorkers);
        server.createContext("/model", exchange -> {
            requests.incrementAndGet();
            if (!headersFirst) ready.countDown();
            try {
                releaseHeaders.await();
                exchange.sendResponseHeaders(200, 0);
                long stopAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < stopAt) {
                    exchange.getResponseBody().write(new byte[8192]); exchange.getResponseBody().flush();
                    if (headersFirst) ready.countDown();
                    Thread.sleep(10);
                }
            } catch (java.io.IOException clientDisconnected) { disconnected.countDown(); }
              catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/model";
            var response = owner.submit(() -> cn.lgs.orbisops.trigger.ops.OpsRunControlContext.withBinding(control, () -> {
                assertThrows(cn.lgs.orbisops.trigger.ops.OpsRunCanceledException.class,
                        () -> new cn.lgs.orbisops.trigger.ops.OpsLlmModelCallExecutor(worker).executeCall(() -> {
                            var retry = new OpsModelRequestRetry("gpt-6-luna", 30_000, events::add); retry.begin();
                            try {
                                return OpsModelHttpClientFactory.restClientBuilder(1, 30).build().get().uri(uri)
                                        .retrieve().body(String.class);
                            } catch (RuntimeException error) { retry.retryDelay(error, false); throw error; }
                            finally { workerEnded.countDown(); }
                        }, null, 30_000));
                return null;
            }));
            assertTrue(ready.await(4, TimeUnit.SECONDS)); registry.markCanceled("cancel-run");
            releaseHeaders.countDown(); response.get(4, TimeUnit.SECONDS);
            assertTrue(workerEnded.await(4, TimeUnit.SECONDS), "Actual transport worker must exit, not only its canceled Future");
            assertTrue(disconnected.await(4, TimeUnit.SECONDS), "Server must observe the original physical connection closing");
            assertEquals(1, requests.get());
            assertFalse(events.stream().anyMatch(e -> "MODEL_CALL_RETRYING".equals(e.getEventType())));
        } finally {
            releaseHeaders.countDown(); server.stop(0); serverWorkers.shutdownNow(); worker.shutdownNow(); owner.shutdownNow();
        }
    }

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
            byte[] response = "{\"model\":\"gpt-6-luna\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            RestClient client = OpsModelHttpClientFactory.restClientBuilder(1, 1).build();
            var events = new CopyOnWriteArrayList<OpsRuntimeEvent>();
            var retry = new OpsModelRequestRetry("gpt-6-luna", 5000, events::add);
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/model";
            retry.begin();
            RuntimeException timeout = assertThrows(RuntimeException.class,
                    () -> client.post().uri(uri).body("{\"model\":\"gpt-6-luna\"}").retrieve().body(String.class));
            assertFalse(OpsModelRequestRetry.interrupted(timeout));
            assertEquals("MODEL_PROVIDER_UNAVAILABLE", OpsModelProviderFailureClassifier.classify(timeout).orElseThrow().code());
            long delay = retry.retryDelay(timeout, false);
            assertTrue(delay >= 400 && delay <= 600);
            Thread.sleep(delay);
            retry.begin();
            String output = client.post().uri(uri).body("{\"model\":\"gpt-6-luna\"}").retrieve().body(String.class);
            retry.verified(com.alibaba.fastjson.JSON.parseObject(output).getString("model"));
            assertEquals(2, requests.get());
            assertEquals(List.of("{\"model\":\"gpt-6-luna\"}", "{\"model\":\"gpt-6-luna\"}"), bodies);
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
                () -> new OpsModelDeadlineHttpRequest(delegate, 10, () -> {}).execute());
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
                () -> new OpsModelDeadlineHttpRequest(delegate, 1000, () -> {}).execute()));
        var retry = new OpsModelRequestRetry("gpt-6-luna", 5000, null);
        retry.begin();
        assertEquals(-1, retry.retryDelay(cancelled, false));
        Thread.currentThread().interrupt();
        try {
            assertSame(cancelled, assertThrows(CancellationException.class,
                    () -> new OpsModelDeadlineHttpRequest(delegate, 0, () -> {}).execute()));
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
                var retry = new OpsModelRequestRetry("gpt-6.1-terra", 5000, null);
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
