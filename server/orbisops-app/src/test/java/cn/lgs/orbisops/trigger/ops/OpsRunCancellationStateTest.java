package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRunCancellationStateTest {

    @Test
    void retainsFinishedCancellationUntilConfiguredExpiry() {
        AtomicLong clock = new AtomicLong(1_000L);
        OpsRunCancellationState state = new OpsRunCancellationState(
                new OpsRunCancellationSettings(60L),
                clock::get);

        state.markCanceled("run-1");
        state.markFinished("run-1");
        clock.addAndGet(60_000L);
        assertTrue(state.isCanceled("run-1"));

        clock.incrementAndGet();
        assertFalse(state.isCanceled("run-1"));
    }

    @Test
    void nonCanceledFinishedRunIsClearedImmediately() {
        OpsRunCancellationState state = new OpsRunCancellationState(
                OpsRunCancellationSettings.defaults(),
                () -> 1_000L);

        state.markFinished("run-2");

        assertFalse(state.isCanceled("run-2"));
    }
}
