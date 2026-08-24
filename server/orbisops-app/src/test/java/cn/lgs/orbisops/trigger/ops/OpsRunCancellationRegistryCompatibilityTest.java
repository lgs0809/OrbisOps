package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OpsRunCancellationRegistryCompatibilityTest {
    @Test
    void requiredRunIdsRejectNullAndWhitespaceWithoutInvokingAction() {
        var registry = new OpsRunCancellationRegistry();
        var calls = new AtomicInteger();
        for (String runId : new String[] {null, "", " \t\n"}) {
            assertThrows(IllegalArgumentException.class, () -> registry.markCanceled(runId));
            assertThrows(IllegalArgumentException.class, () -> registry.onCancel(runId, calls::incrementAndGet));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void optionalRunIdsRemainNoOpsForChecksFinishingAndClearing() {
        var registry = new OpsRunCancellationRegistry();
        registry.markCanceled("keep");
        for (String runId : new String[] {null, "", " \t\n"}) {
            assertFalse(registry.isCanceled(runId));
            assertFalse(registry.cancellationRequested(runId));
            assertDoesNotThrow(() -> registry.markFinished(runId));
            assertDoesNotThrow(() -> registry.clear(runId));
        }
        assertTrue(registry.cancellationRequested("keep"));
    }

    @Test
    void trimmingUsesTheSameRetainedSignalAndLiveHandle() {
        var registry = new OpsRunCancellationRegistry();
        var calls = new AtomicInteger();
        try (var ignored = registry.onCancel(" \trun \n", calls::incrementAndGet)) {
            registry.markCanceled(" run ");
            assertTrue(registry.isCanceled("\trun\n"));
            assertTrue(registry.cancellationRequested("run"));
            assertEquals(1, calls.get());
        }
        registry.clear(" run ");
        assertFalse(registry.cancellationRequested("run"));
    }

    @Test
    void unrelatedInterruptNeverInventsPersistentUserCancellation() {
        var registry = new OpsRunCancellationRegistry();
        try {
            Thread.currentThread().interrupt();
            assertTrue(registry.isCanceled(null));
            assertTrue(registry.isCanceled("unknown"));
            assertFalse(registry.cancellationRequested(null));
            assertFalse(registry.cancellationRequested("unknown"));
            assertThrows(OpsRunCanceledException.class, () -> registry.assertNotCanceled("unknown"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void repeatedCancellationReopensRetentionWithoutLosingItsSignal() {
        var clock = new AtomicLong(1_000);
        var registry = new OpsRunCancellationRegistry(new OpsRunCancellationState(
                OpsRunCancellationSettings.legacyConstructorDefaults(), clock::get));
        registry.markCanceled("run");
        registry.markFinished(" run ");
        clock.addAndGet(59_000);
        registry.markCanceled(" run ");
        clock.addAndGet(61_000);
        assertTrue(registry.cancellationRequested("run"));
        registry.markFinished("run");
        clock.addAndGet(60_000);
        assertTrue(registry.cancellationRequested("run"));
        clock.incrementAndGet();
        assertFalse(registry.cancellationRequested("run"));
    }

    @Test
    void typedPublicHandleKeepsUncheckedIdempotentCloseAndRejectsNullAction() {
        var registry = new OpsRunCancellationRegistry();
        var calls = new AtomicInteger();
        OpsRunCancellationRegistry.CancellationRegistration handle = registry.onCancel("run", calls::incrementAndGet);
        closeWithoutCheckedException(handle);
        closeWithoutCheckedException(handle);
        registry.markCanceled("run");
        assertEquals(0, calls.get());
        assertThrows(NullPointerException.class, () -> registry.onCancel("other", null));
        assertFalse(registry.cancellationRequested("other"));
    }

    @Test
    void concurrentRegistrationAndCancellationNeverMissesTheSignal() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var registry = new OpsRunCancellationRegistry();
        try {
            for (int index = 0; index < 32; index++) {
                String run = "race-" + index;
                var barrier = new CyclicBarrier(2);
                var calls = new AtomicInteger();
                Future<OpsRunCancellationRegistry.CancellationRegistration> handle = pool.submit(() -> {
                    barrier.await(3, TimeUnit.SECONDS);
                    return registry.onCancel(run, calls::incrementAndGet);
                });
                Future<?> canceled = pool.submit(() -> {
                    barrier.await(3, TimeUnit.SECONDS);
                    registry.markCanceled(run);
                    return null;
                });
                try (var registration = handle.get(3, TimeUnit.SECONDS)) {
                    canceled.get(3, TimeUnit.SECONDS);
                    registry.markCanceled(run);
                    assertEquals(1, calls.get(), run);
                }
                registry.clear(run);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void completedCloseCannotReturnWhileItsCancellationActionIsStillRunning() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var registry = new OpsRunCancellationRegistry();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closing = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var registration = registry.onCancel("run", () -> {
            calls.incrementAndGet();
            entered.countDown();
            try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        });
        try {
            var cancellation = pool.submit(() -> registry.markCanceled("run"));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var closed = pool.submit(() -> { closing.countDown(); registration.close(); });
            assertTrue(closing.await(3, TimeUnit.SECONDS));
            assertFalse(closed.isDone());
            release.countDown();
            cancellation.get(3, TimeUnit.SECONDS);
            closed.get(3, TimeUnit.SECONDS);
            registry.markCanceled("run");
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            registration.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private void closeWithoutCheckedException(OpsRunCancellationRegistry.CancellationRegistration handle) {
        handle.close();
    }
}
