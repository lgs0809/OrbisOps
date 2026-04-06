package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.memory.*;
import cn.lgs.orbisops.domain.memory.model.*;
import cn.lgs.orbisops.domain.memory.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL regression. No business outcome is manufactured by SQL; all captures/commits use production services. */
@EnabledIfEnvironmentVariable(named = "ORBISOPS_MEMORY_TEST_URL", matches = ".+orbisops_memory_regression.*")
class ConversationMemoryMySqlTest {
    private JdbcTemplate jdbc;
    private ObjectProvider<JdbcTemplate> provider;
    private JdbcConversationMemoryRepository repository;
    private String session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("ORBISOPS_MEMORY_TEST_URL"),
                System.getenv("ORBISOPS_MEMORY_TEST_USER"), System.getenv("ORBISOPS_MEMORY_TEST_PASSWORD")));
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        repository = new JdbcConversationMemoryRepository(provider);
        session = "ops01-synthetic-" + UUID.randomUUID();
        System.out.println("OPS-01 synthetic fixture session=" + session);
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedLexicalProjectionUsesStableIdInActualPostgresWithoutCallingAnEmbeddingModel() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("ORBISOPS_MEMORY_TEST_PG_URL") != null);
        JdbcTemplate pg = new JdbcTemplate(new DriverManagerDataSource(System.getenv("ORBISOPS_MEMORY_TEST_PG_URL"),
                System.getenv("ORBISOPS_MEMORY_TEST_PG_USER"), System.getenv("ORBISOPS_MEMORY_TEST_PG_PASSWORD")));
        pg.execute("CREATE TABLE IF NOT EXISTS ops01_semantic_regression (id UUID PRIMARY KEY, content TEXT NOT NULL, metadata JSONB NOT NULL)");
        ObjectProvider<JdbcTemplate> pgProvider = mock(ObjectProvider.class);
        when(pgProvider.getIfAvailable()).thenReturn(pg);
        var service = SemanticMemoryWriteApplicationService.withDefaultPolicy(null,
                new OpsSemanticLexicalWriteAdapter(pgProvider, "ops01_semantic_regression"), null);
        var first = repository.capture(message(session, "A", "pg-first", "user", "同一条语义投影"), 24);
        var command = new SemanticMemoryWriteCommand(first.sessionId(), first.userId(), first.role(), first.content(), first.createdAt(), first.metadata(), false);
        assertEquals("lexical", service.write(command).storage());
        assertEquals("lexical", service.write(command).storage());
        assertEquals(1, pg.queryForObject("SELECT COUNT(*) FROM ops01_semantic_regression WHERE metadata->>'session_id'=?", Integer.class, session));
        var second = repository.capture(message(session, "A", "pg-second", "user", "同一条语义投影"), 24);
        assertEquals("lexical", service.write(new SemanticMemoryWriteCommand(second.sessionId(), second.userId(), second.role(), second.content(), second.createdAt(), second.metadata(), false)).storage());
        assertEquals(2, pg.queryForObject("SELECT COUNT(*) FROM ops01_semantic_regression WHERE metadata->>'session_id'=?", Integer.class, session));
        assertEquals(List.of("1", "2"), pg.queryForList("SELECT metadata->>'messageSeq' FROM ops01_semantic_regression WHERE metadata->>'session_id'=? ORDER BY metadata->>'messageSeq'", String.class, session));
    }

    @Test
    void concurrentCaptureIsIdempotentOrderedAcrossInstancesAndProjectBound() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<ColdMemoryMessageSnapshot>> calls = IntStream.range(0, 40)
                    .mapToObj(i -> (Callable<ColdMemoryMessageSnapshot>) () -> new JdbcConversationMemoryRepository(provider)
                            .capture(message(session, "A", "turn-" + (i % 20), "user", "同一请求" + (i % 20)), 24)).toList();
            for (Future<ColdMemoryMessageSnapshot> result : pool.invokeAll(calls)) result.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        var window = new JdbcConversationMemoryRepository(provider).window(session, 100, true).orElseThrow();
        assertEquals(20, window.lastSeq());
        assertEquals(IntStream.rangeClosed(1, 20).mapToObj(i -> (long) i).toList(),
                window.messages().stream().map(ConversationMemoryWindow::sequence).toList());
        assertEquals(60, count("ai_ops_memory_post_processing", session));
        assertEquals(21, ConversationMemoryWindow.sequence(new JdbcConversationMemoryRepository(provider)
                .capture(message(session, "A", "after-restart", "assistant", "恢复后继续"), 24)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.capture(message(session, "B", "bad-scope", "user", "另一个项目"), 24));
        assertThrows(IllegalArgumentException.class,
                () -> repository.capture(message(session, "A", "turn-1", "user", "同一幂等键改参数"), 24));
        assertEquals(21, count("ai_ops_chat_message", session));
    }

    @Test
    void clearedSourcesRejectLateSemanticProjectionAndOldSummaryButKeepSequenceMonotonic() {
        var original = repository.capture(message(session, "A", "before-clear", "user", "禁止删除测试订单"), 24);
        var before = repository.window(session, 20, true).orElseThrow();
        long jobId = repository.pending(session, 1, 1000).get(0);
        var lateJob = repository.claim(jobId, "before-clear-worker", 1000, 10000).orElseThrow();
        var retrieval = new MemoryRetrievalApplicationService(null, null,
                (s, u, q, limit) -> List.of(view(original)), null, () -> null, null, repository);
        var query = new MemoryRetrievalQuery(session, "fixture-user", "订单", "OPS_TROUBLESHOOTING", "A", 5, 20, 5, 5, 1000);
        assertEquals(1, retrieval.retrieve(query).semanticMessages().size());
        new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()))
                .executeWithoutResult(status -> new JdbcColdMemoryRepository(provider, null).clear(session));
        assertTrue(retrieval.retrieve(query).semanticMessages().isEmpty());
        assertTrue(retrieval.retrieve(query).hotMessages().isEmpty());
        assertFalse(repository.complete(lateJob, "LATE", 1001, () -> fail("deleted source must fence local writes")));
        assertFalse(repository.commitSummary(before, 1, "late summary", List.of(original), "RULE"));
        assertEquals(2, ConversationMemoryWindow.sequence(repository.capture(message(session, "A", "after-clear", "user", "新问题"), 24)));
    }

    @Test
    void inverseSummaryCompletionPreservesEarlySourcesAndNewTailAndSurvivesReconstruction() throws Exception {
        for (int i = 1; i <= 4; i++) capture(i, i == 1 ? "禁止重启订单服务；尚未确认连接池等待" : "消息" + i);
        CountDownLatch summarizing = new CountDownLatch(1);
        CountDownLatch finishOld = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var slow = compressor((messages, max) -> {
                summarizing.countDown();
                try { assertTrue(finishOld.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return "旧摘要";
            });
            Future<Boolean> old = pool.submit(() -> slow.compress(compression(true)));
            assertTrue(summarizing.await(10, TimeUnit.SECONDS));
            capture(5, "压缩期间新消息五"); capture(6, "压缩期间新消息六");
            assertTrue(compressor(null).compress(compression(false)));
            finishOld.countDown();
            assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class, () -> old.get(10, TimeUnit.SECONDS)).getCause());
        } finally {
            finishOld.countDown(); pool.shutdownNow();
        }
        var window = repository.window(session, 100, true).orElseThrow();
        assertEquals(1, window.summaryRevision()); assertEquals(4, window.coveredSeq());
        assertEquals(List.of(5L, 6L), window.messages().stream().map(ConversationMemoryWindow::sequence).toList());
        assertEquals(6, count("ai_ops_chat_message", session));
        capture(7, "后续消息七"); capture(8, "后续消息八");
        assertTrue(compressor(null).compress(compression(false)));
        var restored = new JdbcConversationMemoryRepository(provider).window(session, 100, true).orElseThrow();
        assertEquals(2, restored.summaryRevision()); assertEquals(6, restored.coveredSeq());
        assertTrue(restored.summaryContent().contains("禁止重启订单服务"));
        assertTrue(restored.protectedMessages().stream().anyMatch(m -> m.content().contains("尚未确认连接池等待")));
        var retrieval = new MemoryRetrievalApplicationService(null, null, null, null, () -> null, null,
                new JdbcConversationMemoryRepository(provider));
        var recovered = retrieval.retrieve(new MemoryRetrievalQuery(session, "fixture-user", "继续", "", "A", 8, 12, 8, 8, 1000));
        assertTrue(recovered.hotMessages().stream().anyMatch(m -> m.content().contains("尚未确认连接池等待")));
        assertThrows(IllegalArgumentException.class,
                () -> retrieval.retrieve(new MemoryRetrievalQuery(session, "fixture-user", "继续", "", "B", 8, 12, 8, 8, 1000)));
    }

    @Test
    void undispatchedCaptureReplaysAndExtractionCommitFailureRollsBackBothEffectsAndStatus() {
        repository.capture(message(session, "A", "outbox-turn", "user", "项目约定：订单服务仅使用 UTC 时间"), 24);
        assertEquals(3, count("ai_ops_memory_post_processing", session));
        var cold = new ColdMemoryStoreApplicationService(new JdbcColdMemoryRepository(provider, null), () -> true, null);
        var context = new ContextMemoryStoreApplicationService(new JdbcContextMemoryRepository(provider, null), null, null, null);
        AtomicBoolean fail = new AtomicBoolean(true);
        MutableClock clock = new MutableClock(1_000_000L);
        MemoryExtractionPort extraction = message -> List.of(new ColdMemoryItemSnapshot(session, "fixture-user",
                "PROJECT_CONVENTION", "订单服务仅使用 UTC 时间", new java.math.BigDecimal("0.9"), "[]", "user", "fixture",
                Map.of("projectId", "A", "scopeType", "PROJECT", "source", "synthetic-contract-fixture"), "now"));
        ContextMemoryWritePort contextPort = items -> {
            if (fail.getAndSet(false)) throw new IllegalStateException("injected local commit failure");
            context.saveExtractedItemsStrict(items);
        };
        var rejected = worker(cold, extraction, contextPort, () -> task -> { throw new RejectedExecutionException(); }, clock);
        rejected.replayPending(100);
        assertEquals(3, repository.pending(session, 10, clock.millis()).size());
        var recovered = worker(cold, extraction, contextPort, () -> Runnable::run, clock);
        // Target only this fixture's outbox; other tests retain their own replay evidence.
        recovered.submit(new MemoryPostProcessingCommand(view(repository.window(session, 2, true).orElseThrow().messages().get(0)), 24, true));
        assertEquals(0, count("ai_ops_memory_item", session));
        assertEquals("RETRY_WAIT", jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type='EXTRACTION'", String.class, session));
        clock.now += 2000;
        recovered.submit(new MemoryPostProcessingCommand(view(repository.window(session, 2, true).orElseThrow().messages().get(0)), 24, true));
        assertEquals(1, count("ai_ops_memory_item", session));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_post_processing WHERE session_id=? AND status='COMPLETED'", Integer.class, session));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_context_memory WHERE scope_type='PROJECT' AND scope_id='A' AND source_id=?", Integer.class, session));
        recovered.submit(new MemoryPostProcessingCommand(view(repository.window(session, 2, true).orElseThrow().messages().get(0)), 24, true));
        assertEquals(1, count("ai_ops_memory_item", session));
    }

    @Test
    void expiredWorkerCannotCommitAndFailedCompletionTransactionIsReplayable() {
        capture(1, "worker fencing");
        long id = repository.pending(session, 10, 1000).get(0);
        var old = repository.claim(id, "old", 1000, 10).orElseThrow();
        var current = new JdbcConversationMemoryRepository(provider).claim(id, "new", 1011, 100).orElseThrow();
        assertFalse(repository.complete(old, "OLD", 1012, () -> fail("old worker must not execute writes")));
        assertFalse(repository.retry(old, "LATE_FAILURE", 1012));
        assertThrows(IllegalStateException.class, () -> repository.complete(current, "NEW", 1012, () -> {
            jdbc.update("UPDATE ai_ops_chat_message SET content='must rollback' WHERE session_id=?", session);
            throw new IllegalStateException("crash before commit");
        }));
        assertEquals("worker fencing", repository.window(session, 2, true).orElseThrow().messages().get(0).content());
        assertTrue(repository.complete(current, "RECOVERED", 1013, null));
        assertTrue(repository.claim(id, "duplicate", 1200, 10).isEmpty());
    }

    @Test
    void concurrentSameFactHasOneVersionDistinctSourcesAndRealConflictStaysScoped() throws Exception {
        String project = "ops01-fact-" + UUID.randomUUID();
        var governedRepository = new JdbcGovernedMemoryRepository(provider, null);
        var service = new GovernedMemoryApplicationService(governedRepository, null, null,
                () -> "memory-" + UUID.randomUUID(), Clock.systemUTC(), null, Duration.ofHours(24));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = IntStream.range(0, 20).mapToObj(i -> (Callable<GovernedMemoryCreationResult>) () ->
                    service.create(fact(project, "run-" + i, "MySQL 主库"))).toList();
            var results = pool.invokeAll(tasks);
            Set<String> ids = new HashSet<>();
            for (var result : results) ids.add(result.get(20, TimeUnit.SECONDS).snapshot().memoryId());
            assertEquals(1, ids.size());
            String memoryId = ids.iterator().next();
            assertEquals(20, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_source WHERE memory_id=?", Integer.class, memoryId));
            assertEquals("ACTIVE", service.require(memoryId).status());
            assertEquals(1, service.require(memoryId).version());
            assertTrue(service.create(fact(project, "run-1", "MySQL 主库")).duplicate());
            assertEquals("CONFLICT", service.create(fact(project, "conflict", "PostgreSQL 主库")).snapshot().status());
            assertEquals("ACTIVE", service.create(fact(project + "-B", "same-run", "MySQL 主库")).snapshot().status());
        } finally { pool.shutdownNow(); }
    }

    private GovernedMemoryCreateCommand fact(String project, String run, String value) {
        return new GovernedMemoryCreateCommand("PROJECT", project, "PROJECT_FACT", value, "", "db-primary",
                "fixture-user", project, "fixture-agent", session, "USER_ASSERTED", run, false, 0.8, "LOW", List.of(), "fixture-user");
    }

    private MemoryPostProcessingApplicationService worker(ColdMemoryStoreApplicationService cold, MemoryExtractionPort extraction,
            ContextMemoryWritePort context, java.util.function.Supplier<java.util.concurrent.Executor> executor, Clock clock) {
        return new MemoryPostProcessingApplicationService(null, extraction, cold, context, null, executor, null, repository, clock);
    }

    private ColdMemoryMessageSnapshot message(String session, String project, String turn, String role, String content) {
        return new ColdMemoryMessageSnapshot(session, "fixture-user", role, content, "2026-09-08 00:00:00",
                Map.of("projectId", project, "turnId", turn, "fixture", "synthetic-OPS-01"));
    }

    private void capture(int turn, String content) { repository.capture(message(session, "A", "turn-" + turn, "user", content), 24); }
    private int count(String table, String session) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE session_id=?", Integer.class, session); }
    private MemoryCompressionApplicationService compressor(MemoryModelSummaryPort model) {
        return new MemoryCompressionApplicationService(new MemoryCompressionPolicy(new MemoryContentHashPolicy()), model, repository, () -> "now");
    }
    private MemoryCompressionCommand compression(boolean model) { return new MemoryCompressionCommand(session, "fixture-user", List.of(), 4, 2, model, 4000, 24); }
    private MemoryMessageView view(ColdMemoryMessageSnapshot m) { return new MemoryMessageView(m.sessionId(), m.userId(), m.role(), m.content(), m.createdAt(), m.metadata()); }
    private static class MutableClock extends Clock {
        private long now;
        MutableClock(long now) { this.now = now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
