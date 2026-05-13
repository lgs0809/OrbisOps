package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalTableReadinessTest {

    @Test
    void successfulInitializationMustBeCached() {
        FakeRepository repository = new FakeRepository(true);
        RagMultimodalTableReadiness readiness = readiness(repository);

        assertTrue(readiness.ensureReady());
        assertTrue(readiness.ensureReady());
        assertTrue(readiness.ready());
        assertEquals(1, repository.ensureCalls.get());
        assertEquals("table_name", repository.lastTableName);
        assertEquals(2048, repository.lastDimension);
    }

    @Test
    void failedInitializationMustRemainRetryable() {
        FakeRepository repository = new FakeRepository(false);
        RagMultimodalTableReadiness readiness = readiness(repository);

        assertFalse(readiness.ensureReady());
        repository.ensureResult = true;
        assertTrue(readiness.ensureReady());

        assertEquals(2, repository.ensureCalls.get());
        assertTrue(readiness.ready());
    }

    @Test
    void concurrentCallersMustShareOneInFlightInitialization() throws Exception {
        FakeRepository repository = new FakeRepository(true);
        repository.blockInitialization = true;
        RagMultimodalTableReadiness readiness = readiness(repository);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Boolean>> futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> CompletableFuture.supplyAsync(readiness::ensureReady, executor))
                    .toList();

            assertTrue(repository.initializationStarted.await(2, TimeUnit.SECONDS));
            repository.releaseInitialization.countDown();

            for (CompletableFuture<Boolean> future : futures) {
                assertTrue(future.get(2, TimeUnit.SECONDS));
            }
            assertEquals(1, repository.ensureCalls.get());
            assertTrue(readiness.ready());
        } finally {
            repository.releaseInitialization.countDown();
            executor.shutdownNow();
        }
    }

    private RagMultimodalTableReadiness readiness(FakeRepository repository) {
        RagMultimodalSettings settings = new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "secret",
                "v1/embed",
                "model",
                "table_name",
                2048,
                true,
                false,
                true,
                true,
                3,
                144,
                20_971_520L,
                3000,
                8,
                30,
                1);
        return new RagMultimodalTableReadiness(new RagMultimodalTableInitializer(repository, settings));
    }

    private static final class FakeRepository implements IRagMultimodalRepository {

        private final AtomicInteger ensureCalls = new AtomicInteger();
        private final CountDownLatch initializationStarted = new CountDownLatch(1);
        private final CountDownLatch releaseInitialization = new CountDownLatch(1);
        private volatile boolean ensureResult;
        private volatile boolean blockInitialization;
        private volatile String lastTableName;
        private volatile int lastDimension;

        private FakeRepository(boolean ensureResult) {
            this.ensureResult = ensureResult;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean ensureTable(String tableName, int dimension) {
            ensureCalls.incrementAndGet();
            lastTableName = tableName;
            lastDimension = dimension;
            initializationStarted.countDown();
            if (blockInitialization) {
                try {
                    if (!releaseInitialization.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("TEST_INITIALIZATION_RELEASE_TIMEOUT");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("TEST_INITIALIZATION_INTERRUPTED", exception);
                }
            }
            return ensureResult;
        }

        @Override
        public List<RagDocument> search(
                String tableName,
                String vectorLiteral,
                int dimension,
                String filterExpression,
                int topK,
                String provider,
                String model) {
            return List.of();
        }

        @Override
        public void upsert(
                String tableName,
                int dimension,
                String id,
                String content,
                Map<String, Object> metadata,
                String vectorLiteral) {
        }
    }
}
