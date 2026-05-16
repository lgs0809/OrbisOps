package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Retries HTTP generation requests, never an agent or a tool loop. No model fallback. */
public final class OpsResilientOpenAiApi extends OpenAiApi {
    private final long timeoutMillis;
    private final Consumer<OpsRuntimeEvent> eventSink;

    public OpsResilientOpenAiApi(String baseUrl, String apiKey, String completionsPath, String embeddingsPath,
                                int connectTimeoutSeconds, int readTimeoutSeconds, long timeoutMillis,
                                Consumer<OpsRuntimeEvent> eventSink) {
        super(baseUrl, new SimpleApiKey(apiKey), new LinkedMultiValueMap<>(), completionsPath, embeddingsPath,
                OpsModelHttpClientFactory.restClientBuilder(connectTimeoutSeconds, readTimeoutSeconds),
                OpsModelHttpClientFactory.webClientBuilder(connectTimeoutSeconds, readTimeoutSeconds), errorHandler());
        this.timeoutMillis = Math.max(1, timeoutMillis);
        this.eventSink = eventSink;
    }

    @Override
    public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest request, MultiValueMap<String, String> headers) {
        var retry = new OpsModelRequestRetry(request.model(), timeoutMillis, eventSink);
        return OpsNodeDeadlineContext.withDeadline(retry.deadline(), () -> {
            while (true) {
                retry.begin();
                try {
                    var response = super.chatCompletionEntity(request, headers);
                    if (retry.remainingMillis() <= 0) throw new IllegalStateException("MODEL_CALL_DEADLINE_EXHAUSTED");
                    if (response.getBody() == null) throw new IllegalStateException("MODEL_RESPONSE_EMPTY");
                    retry.verified(response.getBody().model());
                    if (response.getBody().choices() == null || response.getBody().choices().stream()
                            .noneMatch(choice -> meaningful(choice.message()))) {
                        throw new IllegalStateException("MODEL_RESPONSE_EMPTY");
                    }
                    return response;
                } catch (RuntimeException error) {
                    if (retry.remainingMillis() <= 0) {
                        throw new IllegalStateException("MODEL_CALL_DEADLINE_EXHAUSTED", error);
                    }
                    long delay = retry.retryDelay(error, false);
                    if (delay < 0) throw safeFailure(error);
                    try { Thread.sleep(delay); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("MODEL_CALL_INTERRUPTED", interrupted);
                    }
                }
            }
        });
    }

    @Override
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest request, MultiValueMap<String, String> headers) {
        // Capture the originating node's deadline before moving onto Reactor's HTTP threads.
        long remaining = OpsNodeDeadlineContext.remainingMillis(timeoutMillis);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(remaining);
        var capturedSink = OpsModelRequestRetry.captureSink(eventSink);
        return Flux.defer(() -> {
            var retry = OpsNodeDeadlineContext.withDeadline(deadline,
                    () -> new OpsModelRequestRetry(request.model(), timeoutMillis, capturedSink));
            return streamAttempt(request, headers, retry, new AtomicBoolean(), new AtomicBoolean())
                    .takeUntilOther(Mono.delay(Duration.ofMillis(Math.max(1, retry.remainingMillis())))
                            .flatMap(ignored -> Mono.error(new IllegalStateException("MODEL_CALL_DEADLINE_EXHAUSTED"))))
                    .onErrorMap(OpsResilientOpenAiApi::safeFailure);
        });
    }

    private Flux<ChatCompletionChunk> streamAttempt(ChatCompletionRequest request, MultiValueMap<String, String> headers,
            OpsModelRequestRetry retry, AtomicBoolean emitted, AtomicBoolean identitySeen) {
        return Flux.defer(() -> {
            retry.begin();
            identitySeen.set(false);
            return super.chatCompletionStream(request, headers);
        })
                .doOnNext(chunk -> {
                    String actual = chunk.model();
                    if (actual != null && !actual.isBlank()) {
                        if (!request.model().equals(actual)) throw new IllegalStateException("MODEL_RESPONSE_IDENTITY_MISMATCH");
                        if (identitySeen.compareAndSet(false, true)) retry.verified(actual);
                    } else if (!identitySeen.get() && chunk.choices() != null && chunk.choices().stream()
                            .anyMatch(choice -> meaningful(choice.delta()))) {
                        // Do not expose text or tool arguments until this response's model is identified.
                        throw new IllegalStateException("MODEL_RESPONSE_IDENTITY_MISSING");
                    }
                })
                // Role/usage-only chunks identify the response but do not establish an answer.
                // Keep trailing usage after a meaningful chunk, and never replay emitted content.
                .filter(chunk -> emitted.get() || chunk.choices() != null && chunk.choices().stream()
                        .anyMatch(choice -> meaningful(choice.delta())))
                .doOnNext(chunk -> emitted.set(true))
                .concatWith(Flux.defer(() -> emitted.get() ? Flux.empty()
                        : Flux.error(new IllegalStateException("MODEL_RESPONSE_EMPTY"))))
                .onErrorResume(error -> {
                    long delay = retry.retryDelay(error, emitted.get());
                    if (delay < 0) return Flux.error(error);
                    return Mono.delay(Duration.ofMillis(delay))
                            .thenMany(streamAttempt(request, headers, retry, emitted, identitySeen));
                });
    }

    private static boolean meaningful(ChatCompletionMessage message) {
        if (message == null) return false;
        Object content = message.content();
        return content instanceof String text ? !text.isBlank()
                || message.toolCalls() != null && !message.toolCalls().isEmpty()
                : content != null || message.toolCalls() != null && !message.toolCalls().isEmpty();
    }

    private static RuntimeException safeFailure(Throwable error) {
        var failure = OpsModelProviderFailureClassifier.classify(error).orElse(null);
        if (failure != null) return new IllegalStateException(failure.code(), error);
        return error instanceof RuntimeException runtime ? runtime : new IllegalStateException("MODEL_CALL_FAILED", error);
    }

    private static ResponseErrorHandler errorHandler() {
        return new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) throws IOException {
                return response.getStatusCode().isError();
            }
            @Override public void handleError(ClientHttpResponse response) throws IOException {
                int status = response.getStatusCode().value();
                String body = new String(response.getBody().readNBytes(4096), StandardCharsets.UTF_8);
                String code = OpsModelProviderFailureClassifier.classify(body)
                        .filter(failure -> failure.code().equals("MODEL_PROVIDER_QUOTA_EXHAUSTED"))
                        .map(OpsModelProviderFailureClassifier.Failure::code)
                        .orElse(status == 401 || status == 403 ? "MODEL_PROVIDER_AUTH_FAILED"
                                : status == 429 ? "MODEL_PROVIDER_RATE_LIMITED"
                                : status == 408 || status == 425 || status >= 500 ? "MODEL_PROVIDER_UNAVAILABLE"
                                : "MODEL_PROVIDER_REQUEST_REJECTED");
                throw new OpsModelRequestRetry.HttpFailure(code, response.getHeaders());
            }
        };
    }
}
