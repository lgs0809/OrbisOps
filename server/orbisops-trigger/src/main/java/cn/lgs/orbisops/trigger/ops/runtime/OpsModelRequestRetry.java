package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One provider request's budget. Tool execution and the ReAct loop are outside this boundary. */
final class OpsModelRequestRetry {
    static final int MAX_ATTEMPTS = 5;
    private final long deadline;
    private final String model;
    private final Consumer<OpsRuntimeEvent> sink;
    private final cn.lgs.orbisops.trigger.ops.OpsRunControlContext.Binding control;
    private int attempt;
    private final long started = System.nanoTime();
    private long attemptStarted = started;

    OpsModelRequestRetry(String model, long timeoutMillis, Consumer<OpsRuntimeEvent> sink) {
        this.model = model;
        this.sink = captureSink(sink);
        this.control = cn.lgs.orbisops.trigger.ops.OpsRunControlContext.current();
        this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                OpsNodeDeadlineContext.remainingMillis(timeoutMillis));
    }

    long deadline() { return deadline; }
    long remainingMillis() { return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())); }

    static Consumer<OpsRuntimeEvent> captureSink(Consumer<OpsRuntimeEvent> configured) {
        if (configured != null) return configured;
        var trace = cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext.current();
        return trace == null ? null : trace::record;
    }

    void begin() {
        assertNotCanceled();
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("MODEL_CALL_INTERRUPTED");
        if (remainingMillis() <= 0) throw new IllegalStateException("MODEL_CALL_DEADLINE_EXHAUSTED");
        attempt++;
        attemptStarted = System.nanoTime();
        emit("MODEL_REQUEST_ATTEMPT", "RUNNING", "正在请求模型。", Map.of());
    }

    /** Returns -1 when retry is prohibited. Never shortens a provider's Retry-After. */
    long retryDelay(Throwable error, boolean emitted) {
        assertNotCanceled();
        if (error instanceof cn.lgs.orbisops.trigger.ops.OpsRunCanceledException) throw (cn.lgs.orbisops.trigger.ops.OpsRunCanceledException) error;
        var details = new java.util.LinkedHashMap<String,Object>();
        details.put("reasonCode", OpsModelProviderFailureClassifier.classify(error)
                .map(OpsModelProviderFailureClassifier.Failure::code).orElse("MODEL_CALL_FAILED"));
        int status = httpStatus(error);
        if (status > 0) details.put("httpStatus", status);
        details.put("responseEmitted", emitted);
        emit("MODEL_REQUEST_FAILED", "FAILED", "本次模型请求未完成。", details);
        if (emitted || attempt >= MAX_ATTEMPTS || Thread.currentThread().isInterrupted() || interrupted(error)) return -1;
        var failure = OpsModelProviderFailureClassifier.classify(error).orElse(null);
        if (failure == null || !(failure.code().equals("MODEL_PROVIDER_UNAVAILABLE")
                || failure.code().equals("MODEL_PROVIDER_RATE_LIMITED"))) return -1;
        long delay = Math.max(retryAfter(error), ThreadLocalRandom.current().nextLong(400, 601) * (1L << (attempt - 1)));
        if (remainingMillis() <= delay + 10) return -1;
        emit("MODEL_CALL_RETRYING", "RUNNING", "模型连接暂时异常，正在自动重试（" + (attempt + 1) + "/" + MAX_ATTEMPTS + "）。",
                Map.of("reasonCode", failure.code(), "nextAttempt", attempt + 1, "delayMs", delay));
        return delay;
    }

    void verified(String actual) {
        verified(actual, 0);
    }
    void verified(String actual, int httpStatus) {
        assertNotCanceled();
        if (actual == null || actual.isBlank()) throw new IllegalStateException("MODEL_RESPONSE_IDENTITY_MISSING");
        if (!model.equals(actual)) throw new IllegalStateException("MODEL_RESPONSE_IDENTITY_MISMATCH");
        var extra=new java.util.LinkedHashMap<String,Object>();extra.put("responseModel",actual);
        if(httpStatus>0)extra.put("httpStatus",httpStatus);
        emit("MODEL_RESPONSE_VERIFIED", "SUCCEEDED", "模型响应身份已核对。", extra);
    }

    void terminal(String reason, boolean streaming) {
        assertNotCanceled();
        emit("MODEL_RESPONSE_TERMINAL", "RECEIVED", "已收到模型响应终止标记。",
                Map.of("finishReason", reason, "streaming", streaming));
    }

    void assertNotCanceled() { if (control != null) control.assertNotCanceled(); }

    private void emit(String type, String status, String summary, Map<String, Object> extra) {
        if (sink == null) return;
        var payload = new java.util.LinkedHashMap<String, Object>(extra);
        payload.put("requestedModel", model);
        payload.put("attempt", attempt);
        payload.put("maxAttempts", MAX_ATTEMPTS);
        payload.put("remainingMs", remainingMillis());
        payload.put("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - attemptStarted));
        payload.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        sink.accept(OpsRuntimeEvent.builder().eventType(type).status(status).summary(summary).payload(payload).build());
    }

    static boolean interrupted(Throwable error) {
        for (int n = 0; error != null && n < 12; n++, error = error.getCause()) {
            if (error instanceof InterruptedException || error instanceof java.util.concurrent.CancellationException
                    || error.getMessage() != null && (error.getMessage().contains("MODEL_CALL_INTERRUPTED")
                    || error.getMessage().contains("MODEL_CALL_DEADLINE_EXHAUSTED"))) return true;
        }
        return false;
    }

    private static long retryAfter(Throwable error) {
        for (int n = 0; error != null && n < 12; n++, error = error.getCause()) {
            if (error instanceof HttpFailure http) return http.retryAfterMillis;
            if (error instanceof WebClientResponseException http) return retryAfter(http.getHeaders());
        }
        return 0;
    }

    static long retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) return 0;
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.min(TimeUnit.DAYS.toSeconds(1), Math.max(0, seconds)) * 1000L;
        } catch (RuntimeException ignored) {
            try {
                return Math.max(0, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - System.currentTimeMillis());
            } catch (RuntimeException invalid) { return 0; }
        }
    }

    static final class HttpFailure extends IllegalStateException {
        final long retryAfterMillis;
        final int httpStatus;
        HttpFailure(String code, HttpHeaders headers) {
            this(code, headers, 0);
        }
        HttpFailure(String code, HttpHeaders headers, int httpStatus) {
            super(code);
            retryAfterMillis = retryAfter(headers);
            this.httpStatus = httpStatus;
        }
    }

    private static int httpStatus(Throwable error) {
        for (int n=0; error!=null && n<12; n++, error=error.getCause()) {
            if (error instanceof HttpFailure http) return http.httpStatus;
            if (error instanceof WebClientResponseException http) return http.getStatusCode().value();
        }
        return 0;
    }
}
