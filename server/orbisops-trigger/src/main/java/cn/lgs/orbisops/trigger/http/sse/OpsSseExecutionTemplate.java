package cn.lgs.orbisops.trigger.http.sse;

import cn.lgs.orbisops.trigger.http.OpsSseFailurePayload;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/** Composition-based SSE lifecycle template; controllers retain all business callbacks. */
@Component
public final class OpsSseExecutionTemplate {

    private final OpsSseEventWriter writer;
    private final LongFunction<SseEmitter> emitterFactory;
    private final OpsSseTelemetry telemetry;

    public OpsSseExecutionTemplate() {
        this(new OpsSseEventWriter(), SseEmitter::new, new OpsSseTelemetry());
    }

    @Autowired
    public OpsSseExecutionTemplate(OpsSseTelemetry telemetry) {
        this(new OpsSseEventWriter(), SseEmitter::new, telemetry);
    }

    OpsSseExecutionTemplate(
            OpsSseEventWriter writer,
            LongFunction<SseEmitter> emitterFactory) {
        this(writer, emitterFactory, new OpsSseTelemetry());
    }

    OpsSseExecutionTemplate(
            OpsSseEventWriter writer,
            LongFunction<SseEmitter> emitterFactory,
            OpsSseTelemetry telemetry) {
        if (writer == null) throw new IllegalArgumentException("SSE_EVENT_WRITER_REQUIRED");
        if (emitterFactory == null) throw new IllegalArgumentException("SSE_EMITTER_FACTORY_REQUIRED");
        if (telemetry == null) throw new IllegalArgumentException("SSE_TELEMETRY_REQUIRED");
        this.writer = writer;
        this.emitterFactory = emitterFactory;
        this.telemetry = telemetry;
    }

    public OpsSseStreamSession open(
            HttpServletResponse response,
            long timeoutMs,
            Lifecycle lifecycle) {
        configureHeaders(response);
        SseEmitter emitter = emitterFactory.apply(Math.max(0L, timeoutMs));
        if (emitter == null) throw new IllegalStateException("SSE_EMITTER_FACTORY_RETURNED_NULL");
        OpsSseStreamSession session = new OpsSseStreamSession(
                emitter,
                writer,
                new OpsSseCompletionGuard());
        session.registerResource(telemetry.opened(session::state));
        Lifecycle safe = lifecycle == null ? Lifecycle.defaults() : lifecycle;
        emitter.onCompletion(() -> {
            session.emitterCompleted();
            safe.onCompletion().run();
        });
        emitter.onTimeout(() -> {
            safe.onTimeout().run();
            session.timeout(payload(safe.timeoutPayload()), safe.cancelTaskOnTimeout());
        });
        emitter.onError(error -> {
            safe.onError().accept(error);
            session.disconnect(safe.cancelTaskOnError());
        });
        return session;
    }

    public void announce(
            OpsSseStreamSession session,
            HttpServletResponse response,
            Object openPayload) {
        requireSession(session);
        if (openPayload != null) session.sendData(openPayload);
        flush(response);
    }

    public void submit(
            OpsSseStreamSession session,
            ExecutorService executor,
            Task task,
            Function<Throwable, Object> failurePayload) {
        requireSession(session);
        if (executor == null) throw new IllegalArgumentException("SSE_EXECUTOR_REQUIRED");
        if (task == null) throw new IllegalArgumentException("SSE_TASK_REQUIRED");
        Function<Throwable, Object> failure = failurePayload == null
                ? OpsSseFailurePayload::failed
                : failurePayload;
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    task.run(session);
                    session.complete();
                } catch (Throwable error) {
                    session.fail(error, safeFailurePayload(failure, error), false);
                }
            });
            session.bindTask(future);
        } catch (RuntimeException error) {
            session.reject(error, safeFailurePayload(failure, error));
        }
    }

    public void failPreflight(
            OpsSseStreamSession session,
            Throwable error,
            Object failurePayload) {
        requireSession(session);
        session.fail(error, failurePayload, false);
    }

    private Object safeFailurePayload(
            Function<Throwable, Object> mapper,
            Throwable error) {
        try {
            Object payload = mapper.apply(error);
            return payload == null ? OpsSseFailurePayload.failed(error) : payload;
        } catch (RuntimeException ignored) {
            return OpsSseFailurePayload.failed(error);
        }
    }

    private Object payload(Supplier<Object> supplier) {
        if (supplier == null) return null;
        try {
            return supplier.get();
        } catch (RuntimeException error) {
            return OpsSseFailurePayload.failed(error);
        }
    }

    private void configureHeaders(HttpServletResponse response) {
        if (response == null) return;
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
    }

    private void flush(HttpServletResponse response) {
        if (response == null) return;
        try {
            response.flushBuffer();
        } catch (Exception ignored) {
        }
    }

    private void requireSession(OpsSseStreamSession session) {
        if (session == null) throw new IllegalArgumentException("SSE_STREAM_SESSION_REQUIRED");
    }

    @FunctionalInterface
    public interface Task {
        void run(OpsSseStreamSession session) throws Exception;
    }

    public record Lifecycle(
            Supplier<Object> timeoutPayload,
            boolean cancelTaskOnTimeout,
            boolean cancelTaskOnError,
            Runnable onCompletion,
            Runnable onTimeout,
            Consumer<Throwable> onError) {

        public Lifecycle {
            timeoutPayload = timeoutPayload == null ? () -> null : timeoutPayload;
            onCompletion = onCompletion == null ? () -> { } : onCompletion;
            onTimeout = onTimeout == null ? () -> { } : onTimeout;
            onError = onError == null ? error -> { } : onError;
        }

        public static Lifecycle defaults() {
            return new Lifecycle(() -> null, false, false, null, null, null);
        }
    }
}
