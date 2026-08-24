package cn.lgs.orbisops.trigger.ops.runtime;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Per-request handle on top of the shared JDK client. Spring can lose access to an
 * already-open body when an interrupt wins the handoff from its response future.
 */
final class OpsCancelableJdkExchange extends HttpClient {
    private final HttpClient client;
    private CompletableFuture<? extends HttpResponse<?>> pendingResponse;
    private boolean aborted;

    OpsCancelableJdkExchange(HttpClient client) {
        this.client = client;
    }

    void abort() {
        CompletableFuture<? extends HttpResponse<?>> pending;
        synchronized (this) {
            if (aborted) return;
            aborted = true;
            pending = pendingResponse;
        }
        if (pending != null) abortResponse(pending);
    }

    private <T> CompletableFuture<HttpResponse<T>> track(CompletableFuture<HttpResponse<T>> future) {
        synchronized (this) {
            if (!aborted) {
                pendingResponse = future;
                return future;
            }
        }
        abortResponse(future);
        return future;
    }

    private static void abortResponse(CompletableFuture<? extends HttpResponse<?>> future) {
        if (future.cancel(true)) return;
        // An already-completed future no longer owns cancellation of the TCP exchange.
        future.thenAccept(response -> {
            if (response.body() instanceof InputStream body) {
                try {
                    body.close();
                } catch (IOException ignored) {
                    // An already-closed stream has no response left to abort.
                }
            }
        });
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler) {
        return track(client.sendAsync(request, bodyHandler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return track(client.sendAsync(request, bodyHandler, pushPromiseHandler));
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        return client.send(request, bodyHandler);
    }

    @Override public Optional<CookieHandler> cookieHandler() { return client.cookieHandler(); }
    @Override public Optional<Duration> connectTimeout() { return client.connectTimeout(); }
    @Override public Redirect followRedirects() { return client.followRedirects(); }
    @Override public Optional<ProxySelector> proxy() { return client.proxy(); }
    @Override public SSLContext sslContext() { return client.sslContext(); }
    @Override public SSLParameters sslParameters() { return client.sslParameters(); }
    @Override public Optional<Authenticator> authenticator() { return client.authenticator(); }
    @Override public Version version() { return client.version(); }
    @Override public Optional<Executor> executor() { return client.executor(); }
}
