package cn.lgs.orbisops.trigger.http.sse;

import cn.lgs.orbisops.trigger.http.OpsSseFailurePayload;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSseExecutionTemplateTest {

    @Test
    void normalExecutionConfiguresHeadersSendsEventsAndCompletesOnce() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        when(executor.submit(task.capture())).thenAnswer(invocation -> future);
        OpsSseExecutionTemplate template = template(emitter);
        OpsSseStreamSession session = template.open(
                response, 30_000L, OpsSseExecutionTemplate.Lifecycle.defaults());

        template.announce(session, response, Map.of("eventType", "OPEN"));
        template.submit(session, executor,
                stream -> stream.sendData(Map.of("eventType", "TOKEN")),
                OpsSseFailurePayload::failed);
        task.getValue().run();

        assertEquals(OpsSseCompletionGuard.State.COMPLETED, session.state());
        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
        verify(response).setContentType("text/event-stream");
        verify(response).setCharacterEncoding("UTF-8");
        verify(response).setHeader("X-Accel-Buffering", "no");
        verify(response).flushBuffer();
    }

    @Test
    void timeoutCancelsTaskAndEmitsTerminalEventOnlyOnce() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> future);
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        OpsSseExecutionTemplate template = template(emitter);
        OpsSseStreamSession session = template.open(
                null,
                30_000L,
                new OpsSseExecutionTemplate.Lifecycle(
                        () -> Map.of("status", "TIMEOUT"),
                        true,
                        true,
                        null,
                        null,
                        null));
        verify(emitter).onTimeout(timeout.capture());
        template.submit(session, executor, stream -> { }, OpsSseFailurePayload::failed);

        timeout.getValue().run();
        timeout.getValue().run();

        assertEquals(OpsSseCompletionGuard.State.TIMED_OUT, session.state());
        verify(future, times(1)).cancel(true);
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
    }

    @Test
    void businessFailureWithNullMessageUsesStableFailurePayload() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        when(executor.submit(task.capture())).thenAnswer(invocation -> future);
        OpsSseExecutionTemplate template = template(emitter);
        OpsSseStreamSession session = template.open(
                null, 30_000L, OpsSseExecutionTemplate.Lifecycle.defaults());

        template.submit(session, executor,
                stream -> { throw new IllegalStateException((String) null); },
                OpsSseFailurePayload::failed);
        task.getValue().run();

        assertEquals(OpsSseCompletionGuard.State.FAILED, session.state());
        assertEquals("IllegalStateException",
                OpsSseFailurePayload.failed(new IllegalStateException()).get("summary"));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
    }

    @Test
    void clientDisconnectCanKeepBackgroundTaskRunningAndRejectFurtherSends() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ExecutorService executor = mock(ExecutorService.class);
        Future<?> future = mock(Future.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> future);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> error = ArgumentCaptor.forClass(Consumer.class);
        OpsSseExecutionTemplate template = template(emitter);
        OpsSseStreamSession session = template.open(
                null,
                30_000L,
                new OpsSseExecutionTemplate.Lifecycle(
                        () -> null, false, false, null, null, null));
        verify(emitter).onError(error.capture());
        template.submit(session, executor, stream -> { }, OpsSseFailurePayload::failed);

        error.getValue().accept(new IllegalStateException("client closed"));

        assertEquals(OpsSseCompletionGuard.State.DISCONNECTED, session.state());
        verify(future, never()).cancel(true);
        assertThrows(IllegalStateException.class,
                () -> session.sendData(Map.of("eventType", "LATE")));
        assertFalse(session.trySendData(Map.of("eventType", "LATE")));
    }

    @Test
    void executorRejectionFailsStreamWithoutEscapingController() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class)))
                .thenThrow(new RejectedExecutionException("busy"));
        OpsSseExecutionTemplate template = template(emitter);
        OpsSseStreamSession session = template.open(
                null, 30_000L, OpsSseExecutionTemplate.Lifecycle.defaults());

        template.submit(session, executor, stream -> { }, OpsSseFailurePayload::failed);

        assertEquals(OpsSseCompletionGuard.State.REJECTED, session.state());
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
    }

    @Test
    void duplicateCompletionAndResourceCloseAreIdempotent() {
        SseEmitter emitter = mock(SseEmitter.class);
        OpsSseStreamSession session = new OpsSseStreamSession(
                emitter, new OpsSseEventWriter(), new OpsSseCompletionGuard());
        AtomicBoolean closed = new AtomicBoolean(false);
        session.registerResource(() -> {
            if (!closed.compareAndSet(false, true)) {
                throw new IllegalStateException("closed twice");
            }
        });

        assertTrue(session.complete());
        assertFalse(session.complete());
        session.emitterCompleted();

        assertTrue(closed.get());
        verify(emitter, times(1)).complete();
    }

    private OpsSseExecutionTemplate template(SseEmitter emitter) {
        return new OpsSseExecutionTemplate(new OpsSseEventWriter(), timeout -> emitter);
    }
}
