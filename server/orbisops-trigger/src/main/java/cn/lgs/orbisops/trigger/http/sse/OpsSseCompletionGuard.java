package cn.lgs.orbisops.trigger.http.sse;

import java.util.concurrent.atomic.AtomicReference;

/** Single terminal transition guard for one SSE stream. */
public final class OpsSseCompletionGuard {

    public enum State {
        OPEN,
        COMPLETED,
        FAILED,
        TIMED_OUT,
        DISCONNECTED,
        REJECTED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    public State state() {
        return state.get();
    }

    public boolean tryTerminate(State terminalState) {
        if (terminalState == null || terminalState == State.OPEN) {
            throw new IllegalArgumentException("SSE_TERMINAL_STATE_REQUIRED");
        }
        return state.compareAndSet(State.OPEN, terminalState);
    }
}
