package cn.lgs.orbisops.trigger.ops.runtime;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

/** Real loopback TLS disconnects; no model response or quality is simulated as a pass. */
class OpsModelTlsRecoveryTest {
    @Test void retriesTheObservedWrappedHandshakeDisconnect() {
        var failure = new IllegalStateException("request failed", new SSLHandshakeException("Remote host terminated the handshake"));
        assertThat(OpsModelProviderFailureClassifier.classify(failure)).get()
            .extracting(OpsModelProviderFailureClassifier.Failure::code).isEqualTo("MODEL_PROVIDER_UNAVAILABLE");
        var retry = new OpsModelRequestRetry("gpt-5.6-luna", 60_000, null);
        for (int attempt = 1; attempt <= 5; attempt++) {
            retry.begin();
            if (attempt < 5) assertThat(retry.retryDelay(failure, false)).isPositive();
            else assertThat(retry.retryDelay(failure, false)).isEqualTo(-1);
        }
    }

    @Test void certificateErrorsNeverBecomeRetriesViaGenericIoWrapper() {
        var handshake = new SSLHandshakeException("Remote host terminated the handshake");
        handshake.initCause(new CertificateException("certificate expired"));
        var error = new IllegalStateException("I/O error", handshake);
        assertThat(OpsModelProviderFailureClassifier.classify(error)).isEmpty();
        var retry = new OpsModelRequestRetry("gpt-5.6-luna", 60_000, null);
        retry.begin();
        assertThat(retry.retryDelay(error, false)).isEqualTo(-1);
        assertThat(OpsModelProviderFailureClassifier.classify(new IllegalStateException("I/O error",
            new SSLHandshakeException("PKIX path building failed")))).isEmpty();
    }

    @Test void tlsDisconnectAfterExposedContentIsNotReplayed() {
        var retry = new OpsModelRequestRetry("gpt-5.6-luna", 60_000, null);
        retry.begin();
        assertThat(retry.retryDelay(new SSLHandshakeException("Remote host terminated the handshake"), true)).isEqualTo(-1);
    }

    @Test void realStreamingTlsDisconnectStopsAfterFiveModelAttempts() throws Exception {
        var events = new CopyOnWriteArrayList<OpsRuntimeEvent>();
        var sockets = new AtomicInteger();
        var worker = Executors.newSingleThreadExecutor();
        try (var server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))) {
            var listener = worker.submit(() -> {
                while (!server.isClosed()) {
                    try (var client = server.accept()) {
                        client.setSoTimeout(2000);
                        client.getInputStream().read(); // Receive a real ClientHello, then close before any response.
                        sockets.incrementAndGet();
                    } catch (java.io.IOException ignored) { }
                }
            });
            var api = new OpsResilientOpenAiApi("https://127.0.0.1:" + server.getLocalPort(), "fixture-key",
                "/v1/chat/completions", "/v1/embeddings", 2, 2, 20_000, events::add);
            var request = new OpenAiApi.ChatCompletionRequest(List.of(), "gpt-5.6-luna", (Double) null, true);
            assertThatThrownBy(() -> api.chatCompletionStream(request).collectList().block(Duration.ofSeconds(25)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("MODEL_PROVIDER_UNAVAILABLE");
            assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_REQUEST_ATTEMPT")).hasSize(5);
            assertThat(events).filteredOn(e -> e.getEventType().equals("MODEL_CALL_RETRYING")).hasSize(4);
            assertThat(events).noneMatch(e -> e.getEventType().equals("MODEL_RESPONSE_VERIFIED"));
            assertThat(sockets.get()).isGreaterThanOrEqualTo(5);
        } finally { worker.shutdownNow(); }
    }
}
