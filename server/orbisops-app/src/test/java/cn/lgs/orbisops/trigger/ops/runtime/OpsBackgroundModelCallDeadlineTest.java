package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class OpsBackgroundModelCallDeadlineTest {
    @Test void clampsRequestedBudgetAndPropagatesTraceWithoutLeakingCallerContext() {
        var trace = new OpsLlmTraceContext.Trace(null, null, "background", "node", "LLM", "test", "test");
        var remaining = OpsLlmTraceContext.withTrace(trace, () -> OpsBackgroundModelCallDeadline.call(120, () -> {
            assertSame(trace, OpsLlmTraceContext.current());
            assertTrue(Thread.currentThread().isDaemon());
            return OpsNodeDeadlineContext.remainingMillis(120_000);
        }));
        assertTrue(remaining > 55_000 && remaining <= 60_000);
        assertNull(OpsNodeDeadlineContext.captureDeadline());
        assertNull(OpsLlmTraceContext.current());
    }

    @Test void totalDeadlineCancelsWorkAndLeavesAnIndependentCallAvailable() throws Exception {
        var started = new CountDownLatch(1); var cancelled = new CountDownLatch(1);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(200);
        var failure = assertThrows(OpsBackgroundModelCallDeadline.DeadlineExceeded.class,
                () -> OpsNodeDeadlineContext.withDeadline(deadline, () -> OpsBackgroundModelCallDeadline.call(60, () -> {
                    started.countDown();
                    try { Thread.sleep(10_000); }
                    catch (InterruptedException interrupted) { cancelled.countDown(); Thread.currentThread().interrupt(); }
                    return "late result";
                })));
        assertEquals("BACKGROUND_MODEL_CALL_TIMEOUT", failure.getMessage());
        assertEquals(0, started.getCount());
        assertTrue(cancelled.await(2, TimeUnit.SECONDS));
        assertEquals("available", OpsBackgroundModelCallDeadline.call(60, () -> "available"));
        assertNull(OpsNodeDeadlineContext.captureDeadline());
    }

    @Test void expiredBudgetNeverDispatchesAndPermanentFailureIsNotReclassified() {
        assertThrows(OpsBackgroundModelCallDeadline.DeadlineExceeded.class,
                () -> OpsNodeDeadlineContext.withDeadline(System.nanoTime() - 1, () ->
                        OpsBackgroundModelCallDeadline.call(60, () -> { fail("must not dispatch"); return ""; })));
        var permanent = new IllegalArgumentException("invalid source");
        assertSame(permanent, assertThrows(IllegalArgumentException.class,
                () -> OpsBackgroundModelCallDeadline.call(60, () -> { throw permanent; })));
    }
}
