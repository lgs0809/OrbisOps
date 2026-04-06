package cn.lgs.orbisops.trigger.http.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Mutable lifecycle holder for one SSE connection; business state must remain outside. */
public final class OpsSseStreamSession {

    private final SseEmitter emitter;
    private final OpsSseEventWriter writer;
    private final OpsSseCompletionGuard completion;
    private final AtomicReference<Future<?>> task = new AtomicReference<>();
    private final List<AutoCloseable> resources = new CopyOnWriteArrayList<>();
    private final AtomicBoolean resourcesClosed = new AtomicBoolean(false);

    public OpsSseStreamSession(
            SseEmitter emitter,
            OpsSseEventWriter writer,
            OpsSseCompletionGuard completion) {
        if (emitter == null) throw new IllegalArgumentException("SSE_EMITTER_REQUIRED");
        if (writer == null) throw new IllegalArgumentException("SSE_EVENT_WRITER_REQUIRED");
        if (completion == null) throw new IllegalArgumentException("SSE_COMPLETION_GUARD_REQUIRED");
        this.emitter = emitter;
        this.writer = writer;
        this.completion = completion;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public OpsSseCompletionGuard.State state() {
        return completion.state();
    }

    public boolean isOpen() {
        return completion.isOpen();
    }

    public void bindTask(Future<?> future) {
        if (future == null) return;
        if (!task.compareAndSet(null, future)) {
            future.cancel(true);
            throw new IllegalStateException("SSE_TASK_ALREADY_BOUND");
        }
        if (!completion.isOpen()) future.cancel(true);
    }

    public void registerResource(AutoCloseable resource) {
        if (resource == null) return;
        if (resourcesClosed.get()) {
            close(resource);
            return;
        }
        resources.add(resource);
        if (resourcesClosed.get() && resources.remove(resource)) close(resource);
    }

    public void sendData(Object payload) {
        requireOpen();
        writer.sendData(emitter, payload);
    }

    public void send(SseEmitter.SseEventBuilder event) {
        requireOpen();
        writer.send(emitter, event);
    }

    public boolean trySendData(Object payload) {
        if (!completion.isOpen()) return false;
        try {
            writer.sendData(emitter, payload);
            return true;
        } catch (RuntimeException error) {
            disconnect(false);
            return false;
        }
    }

    public boolean trySend(SseEmitter.SseEventBuilder event) {
        if (!completion.isOpen()) return false;
        try {
            writer.send(emitter, event);
            return true;
        } catch (RuntimeException error) {
            disconnect(false);
            return false;
        }
    }

    public boolean complete() {
        if (!completion.tryTerminate(OpsSseCompletionGuard.State.COMPLETED)) return false;
        closeResources();
        emitter.complete();
        return true;
    }

    public boolean fail(Throwable error, Object failurePayload, boolean cancelTask) {
        return terminateWithPayload(
                OpsSseCompletionGuard.State.FAILED,
                failurePayload,
                cancelTask,
                error);
    }

    public boolean reject(Throwable error, Object failurePayload) {
        return terminateWithPayload(
                OpsSseCompletionGuard.State.REJECTED,
                failurePayload,
                false,
                error);
    }

    public boolean timeout(Object timeoutPayload, boolean cancelTask) {
        return terminateWithPayload(
                OpsSseCompletionGuard.State.TIMED_OUT,
                timeoutPayload,
                cancelTask,
                null);
    }

    public boolean disconnect(boolean cancelTask) {
        if (!completion.tryTerminate(OpsSseCompletionGuard.State.DISCONNECTED)) {
            closeResources();
            return false;
        }
        if (cancelTask) cancelTask();
        closeResources();
        return true;
    }

    public void emitterCompleted() {
        if (completion.tryTerminate(OpsSseCompletionGuard.State.DISCONNECTED)) {
            closeResources();
            return;
        }
        closeResources();
    }

    private boolean terminateWithPayload(
            OpsSseCompletionGuard.State state,
            Object payload,
            boolean cancelTask,
            Throwable error) {
        if (!completion.tryTerminate(state)) return false;
        if (cancelTask) cancelTask();
        if (payload != null) {
            try {
                writer.sendData(emitter, payload);
            } catch (RuntimeException ignored) {
            }
        }
        closeResources();
        if (error != null && payload == null) emitter.completeWithError(error);
        else emitter.complete();
        return true;
    }

    private void cancelTask() {
        Future<?> future = task.get();
        if (future != null) future.cancel(true);
    }

    private void closeResources() {
        if (!resourcesClosed.compareAndSet(false, true)) return;
        for (AutoCloseable resource : resources) close(resource);
        resources.clear();
    }

    private void close(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }

    private void requireOpen() {
        if (!completion.isOpen()) {
            throw new IllegalStateException("SSE_SESSION_CLOSED:" + completion.state());
        }
    }
}
