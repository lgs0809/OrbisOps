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

/** Keeps the native JDK transport timeout distinct from an early caller cancellation. */
final class OpsModelDeadlineHttpRequest implements ClientHttpRequest {
    private final ClientHttpRequest delegate;
    private final long timeoutNanos;

    OpsModelDeadlineHttpRequest(ClientHttpRequest delegate, long timeoutMillis) {
        this.delegate = delegate;
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    @Override public HttpMethod getMethod() { return delegate.getMethod(); }
    @Override public URI getURI() { return delegate.getURI(); }
    @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
    @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
    @Override public OutputStream getBody() throws IOException { return delegate.getBody(); }

    @Override public ClientHttpResponse execute() throws IOException {
        long started = System.nanoTime();
        try {
            return delegate.execute();
        } catch (CancellationException cancelled) {
            // Spring's JDK timeout handler can cancel its future before get() wraps the
            // cancellation. Only an elapsed native read deadline is a transport timeout.
            // Caller cancellation and interruption retain their non-retryable identity.
            if (Thread.currentThread().isInterrupted() || System.nanoTime() - started < timeoutNanos) {
                throw cancelled;
            }
            HttpTimeoutException timeout = new HttpTimeoutException("Model HTTP read deadline exceeded");
            timeout.addSuppressed(cancelled);
            throw timeout;
        }
    }
}
