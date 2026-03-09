package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsTraceAwareExecutorFactoryTest {

    private final OpsTraceAwareExecutorFactory factory = new OpsTraceAwareExecutorFactory();

    @Test
    void createsBoundedDaemonPoolWithStableThreadPrefix() throws Exception {
        ThreadPoolExecutor executor = factory.create(new OpsExecutorPoolSettings(
                1, 2, 3, 15L, "AbortPolicy", "ops-unit-"));
        try {
            Future<String> thread = executor.submit(() ->
                    Thread.currentThread().getName() + ":" + Thread.currentThread().isDaemon());

            assertEquals(1, executor.getCorePoolSize());
            assertEquals(2, executor.getMaximumPoolSize());
            assertEquals(3, executor.getQueue().remainingCapacity());
            assertEquals("ops-unit-1:true", thread.get());
            assertEquals(0L, factory.rejectedCount(executor));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void countsSaturationBeforeDelegatingToConfiguredRejectionPolicy() throws Exception {
        ThreadPoolExecutor executor = factory.create(new OpsExecutorPoolSettings(
                1, 1, 1, 15L, "AbortPolicy", "ops-reject-"));
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> await(release));
            executor.execute(() -> await(release));

            org.junit.jupiter.api.Assertions.assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(() -> { }));

            assertEquals(1L, factory.rejectedCount(executor));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void unknownAndBlankPoliciesPreserveCallerRunsFallback() {
        assertTrue(factory.rejectionHandler(" ")
                instanceof ThreadPoolExecutor.CallerRunsPolicy);
        assertTrue(factory.rejectionHandler("unknown")
                instanceof ThreadPoolExecutor.CallerRunsPolicy);
        assertTrue(factory.rejectionHandler("DiscardOldestPolicy")
                instanceof ThreadPoolExecutor.DiscardOldestPolicy);
    }
}
