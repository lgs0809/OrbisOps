package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import cn.lgs.orbisops.trigger.ops.OpsRunControlContext;

/** Keeps the native JDK transport timeout distinct from an early caller cancellation. */
final class OpsModelDeadlineHttpRequest implements ClientHttpRequest {
    private final ClientHttpRequest delegate;
    private final long timeoutNanos;
    private final Runnable abortExchange;

    OpsModelDeadlineHttpRequest(ClientHttpRequest delegate, long timeoutMillis, Runnable abortExchange) {
        this.delegate = delegate;
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        this.abortExchange = abortExchange;
    }

    @Override public HttpMethod getMethod() { return delegate.getMethod(); }
    @Override public URI getURI() { return delegate.getURI(); }
    @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
    @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
    @Override public OutputStream getBody() throws IOException { return delegate.getBody(); }

    @Override public ClientHttpResponse execute() throws IOException {
        long started = System.nanoTime();
        var binding = OpsRunControlContext.current();
        if (binding != null) binding.assertNotCanceled();
        Thread transportThread = Thread.currentThread();
        var registration = binding == null ? null : binding.registry().onCancel(binding.runId(), () -> {
            try {
                abortExchange.run();
            } finally {
                transportThread.interrupt();
            }
        });
        boolean handedOff = false;
        try {
            ClientHttpResponse response = delegate.execute();
            if (binding != null) binding.assertNotCanceled();
            if (registration == null) return response;
            handedOff = true;
            return new ClientHttpResponse() {
                @Override public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
                    return response.getStatusCode();
                }
                @Override public String getStatusText() throws IOException { return response.getStatusText(); }
                @Override public HttpHeaders getHeaders() { return response.getHeaders(); }
                @Override public java.io.InputStream getBody() throws IOException { return response.getBody(); }
                @Override public void close() {
                    try {
                        if (binding.registry().cancellationRequested(binding.runId())) {
                            abortExchange.run();
                        } else {
                            response.close();
                        }
                    } finally {
                        registration.close();
                    }
                }
            };
        } catch (CancellationException cancelled) {
            if (binding != null) binding.assertNotCanceled();
            // Spring's JDK timeout handler can cancel its future before get() wraps the
            // cancellation. Only an elapsed native read deadline is a transport timeout.
            // Caller cancellation and interruption retain their non-retryable identity.
            if (Thread.currentThread().isInterrupted() || System.nanoTime() - started < timeoutNanos) {
                throw cancelled;
            }
            HttpTimeoutException timeout = new HttpTimeoutException("Model HTTP read deadline exceeded");
            timeout.addSuppressed(cancelled);
            throw timeout;
        } catch (IOException | RuntimeException error) {
            if (binding != null) binding.assertNotCanceled();
            throw error;
        } finally {
            if (registration != null && !handedOff) {
                try {
                    if (binding.registry().cancellationRequested(binding.runId())) abortExchange.run();
                } finally {
                    registration.close();
                }
            }
        }
    }
}
