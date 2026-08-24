package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual MySQL transaction recovery, using only isolated regression-schema rows. */
@EnabledIfEnvironmentVariable(named = "ORBISOPS_MEMORY_TEST_URL", matches = ".+orbisops_memory_regression.*")
class GovernedMemoryTransactionMySqlTest {
    private JdbcTemplate jdbc;
    private ObjectProvider<JdbcTemplate> provider;
    private JdbcGovernedMemoryRepository memories;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("ORBISOPS_MEMORY_TEST_URL"),
                System.getenv("ORBISOPS_MEMORY_TEST_USER"), System.getenv("ORBISOPS_MEMORY_TEST_PASSWORD")));
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        memories = new JdbcGovernedMemoryRepository(provider, null);
    }

    @Test
    void actualMysqlDeadlockRestartsOwnedTransactionAndBothWritersFinish() throws Exception {
        String left = key(), right = key(), leftEffect = key(), rightEffect = key();
        insert(left); insert(right);
        var bothLocked = new CountDownLatch(2);
        var attempts = new AtomicInteger();
        var databaseDeadlocks = new AtomicInteger();
        Set<Long> connections = ConcurrentHashMap.newKeySet();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var calls = List.of(writer(left, right, leftEffect, bothLocked, attempts, databaseDeadlocks, connections),
                    writer(right, left, rightEffect, bothLocked, attempts, databaseDeadlocks, connections));
            for (var future : pool.invokeAll(calls)) assertTrue(future.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertTrue(databaseDeadlocks.get() >= 1, "must reproduce vendor deadlock 1213, not a mocked success");
        assertTrue(attempts.get() >= 3);
        assertEquals(attempts.get(), connections.size(), "every recovery attempt uses a new physical transaction");
        assertEquals(1, count(leftEffect));
        assertEquals(1, count(rightEffect));
    }

    @Test
    void wrappedContentionRollsBackLocalEffectsBeforeNewAttempt() {
        String lock = key(), effect = key();
        var attempts = new AtomicInteger();
        Set<Long> connections = ConcurrentHashMap.newKeySet();
        assertEquals("committed", memories.withLogicalLock(lock, () -> {
            connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
            assertEquals(0, count(effect), "previous failed transaction must leave no effect");
            insert(effect);
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("safe audit wrapper", new CannotAcquireLockException("synthetic contention"));
            }
            return "committed";
        }));
        assertEquals(2, attempts.get());
        assertEquals(2, connections.size());
        assertEquals(1, count(effect));
    }

    @Test
    void repeatedContentionExhaustsBoundAndLeavesNoPartialEffects() {
        String lock = key(), effect = key();
        var attempts = new AtomicInteger();
        assertThrows(CannotAcquireLockException.class, () -> memories.withLogicalLock(lock, () -> {
            attempts.incrementAndGet();
            insert(effect);
            throw new CannotAcquireLockException("synthetic repeated contention");
        }));
        assertEquals(5, attempts.get());
        assertEquals(0, count(effect));
        assertEquals(0, count(lock));
    }

    @Test
    void businessFailureRollsBackWithoutRetrying() {
        String lock = key(), effect = key();
        var attempts = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> memories.withLogicalLock(lock, () -> {
            attempts.incrementAndGet(); insert(effect);
            throw new IllegalArgumentException("synthetic rejected command");
        }));
        assertEquals(1, attempts.get());
        assertEquals(0, count(effect));
    }

    @Test
    void joinedTaskCompletionPropagatesContentionThenReplaysWholeAtomicUnit() {
        var conversations = new JdbcConversationMemoryRepository(provider);
        String session = "memory-tx-" + UUID.randomUUID(), lock = key(), effect = key();
        conversations.capture(new ColdMemoryMessageSnapshot(session, "fixture-user", "user", "transaction regression",
                "2026-10-10 00:00:00", Map.of("projectId", "fixture-project", "turnId", "turn-1")), 24);
        long id = conversations.pending(session, 1, 1000).get(0);
        var job = conversations.claim(id, "fixture-worker", 1000, 10000).orElseThrow();
        var attempts = new AtomicInteger();
        assertThrows(CannotAcquireLockException.class, () -> conversations.complete(job, "EXTRACTED", 1001,
                () -> memories.withLogicalLock(lock, () -> {
                    attempts.incrementAndGet(); insert(effect);
                    throw new CannotAcquireLockException("synthetic joined contention");
                })));
        assertEquals(1, attempts.get(), "never retry inside a rollback-only outer task transaction");
        assertEquals(0, count(effect));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?", String.class, id));
        assertTrue(conversations.complete(job, "RECOVERED", 1002, () -> memories.withLogicalLock(lock, () -> {
            insert(effect); return true;
        })));
        assertEquals(1, count(effect));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?", String.class, id));
    }

    @Test
    void callerCannotCatchJoinedFailureAndCommitPartialWrites() {
        String lock = key(), innerEffect = key(), outerEffect = key();
        var attempts = new AtomicInteger();
        var outer = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(UnexpectedRollbackException.class, () -> outer.executeWithoutResult(status -> {
            assertThrows(CannotAcquireLockException.class, () -> memories.withLogicalLock(lock, () -> {
                attempts.incrementAndGet(); insert(innerEffect);
                throw new CannotAcquireLockException("synthetic joined contention");
            }));
            insert(outerEffect);
        }));
        assertEquals(1, attempts.get());
        assertEquals(0, count(innerEffect));
        assertEquals(0, count(outerEffect));
    }

    private Callable<Boolean> writer(String own, String other, String effect, CountDownLatch bothLocked,
                                     AtomicInteger attempts, AtomicInteger deadlocks, Set<Long> connections) {
        return () -> memories.withLogicalLock(own, () -> {
            attempts.incrementAndGet();
            connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
            insert(effect);
            bothLocked.countDown();
            try { assertTrue(bothLocked.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            try {
                jdbc.update("UPDATE ai_ops_memory_logical_lock SET lock_key=lock_key WHERE lock_key=?", other);
            } catch (PessimisticLockingFailureException contention) {
                Throwable root = contention.getMostSpecificCause();
                assertInstanceOf(SQLException.class, root);
                assertEquals(1213, ((SQLException) root).getErrorCode());
                deadlocks.incrementAndGet();
                throw contention;
            }
            return true;
        });
    }

    private void insert(String key) { jdbc.update("INSERT INTO ai_ops_memory_logical_lock(lock_key) VALUES (?)", key); }
    private int count(String key) { return jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_logical_lock WHERE lock_key=?", Integer.class, key); }
    private String key() { return UUID.randomUUID().toString().replace("-", "").repeat(2); }
}
