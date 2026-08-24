package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OpsRunCancellationStateConcurrencyTest {
    private static final int ROUNDS = 4_096;

    @Test
    @Timeout(30)
    void expiredReadCleanupCannotEraseAReissuedActiveCancellation() throws Exception {
        race(true);
    }

    @Test
    @Timeout(30)
    void finishingAnUncanceledRunCannotEraseConcurrentCancellation() throws Exception {
        race(false);
    }

    private void race(boolean expiredRead) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        int lostSignals = 0;
        try {
            for (int round = 0; round < ROUNDS; round++) {
                var clock = new AtomicLong(1_000);
                var registry = new OpsRunCancellationRegistry(new OpsRunCancellationState(
                        OpsRunCancellationSettings.legacyConstructorDefaults(), clock::get));
                if (expiredRead) {
                    registry.markCanceled("run");
                    registry.markFinished("run");
                    clock.addAndGet(60_001);
                }
                var ready = new CyclicBarrier(3);
                Future<?> cancellation = workers.submit(() -> {
                    ready.await(3, TimeUnit.SECONDS);
                    registry.markCanceled("run");
                    return null;
                });
                Future<?> maintenance = workers.submit(() -> {
                    ready.await(3, TimeUnit.SECONDS);
                    if (expiredRead) registry.isCanceled("unrelated-probe");
                    else registry.markFinished("run");
                    return null;
                });
                ready.await(3, TimeUnit.SECONDS);
                cancellation.get(3, TimeUnit.SECONDS);
                maintenance.get(3, TimeUnit.SECONDS);
                if (!registry.cancellationRequested("run")) lostSignals++;
            }
            assertEquals(0, lostSignals, "Read cleanup / finishing must not clear a concurrently reissued cancellation");
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
}
