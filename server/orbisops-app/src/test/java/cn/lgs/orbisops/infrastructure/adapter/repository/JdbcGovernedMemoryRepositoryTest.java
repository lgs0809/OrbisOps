package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryVersionSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcGovernedMemoryRepositoryTest {

    @Test
    void insertAndAppendVersionPersistTypedModels() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcGovernedMemorySchemaInitializer initializer = mock(JdbcGovernedMemorySchemaInitializer.class);
        JdbcGovernedMemoryRepository repository = repository(jdbc, initializer);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.insert(snapshot());
        repository.appendVersion(new GovernedMemoryVersionSnapshot(
                "memory-1", 2, "hash-2", "ACTIVE", "content",
                "normalized", "run-1", "user-1"));

        verify(initializer, times(2)).initialize();
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_memory\n")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_memory_version")),
                any(Object[].class));
    }

    @Test
    void conflictVerifyAndAuditPersistGovernanceRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcGovernedMemoryRepository repository = repository(jdbc, null);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals(true, repository.markConflict("memory-old"));
        String conflictId = repository.recordConflict(
                MemoryScope.PROJECT,
                "demo-project",
                "mysql-primary",
                "memory-old",
                "memory-new",
                "user-1");
        assertEquals(true, conflictId.startsWith("mem-conflict-"));
        assertEquals(true, repository.verifyProjectFact(
                "memory-new",
                List.of(Map.of("proofId", "proof-1"))));
        repository.recordAudit(
                "memory-new",
                "PROJECT_FACT_VERIFIED",
                "user-1",
                snapshot(),
                "");

        verify(jdbc).update(argThat(sql -> sql.contains("SET status = 'CONFLICT'")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_memory_conflict")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("confidence = GREATEST(confidence, 0.9000)")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_memory_audit")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingStoreFailsClosed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcGovernedMemoryRepository repository = new JdbcGovernedMemoryRepository(provider, null);

        assertThrows(IllegalStateException.class, () -> repository.insert(snapshot()));
    }

    @Test
    void mutationMethodsDeclareMysqlTransactionBoundary() throws NoSuchMethodException {
        assertTransaction("insert", GovernedMemorySnapshot.class);
        assertTransaction("appendVersion", GovernedMemoryVersionSnapshot.class);
        assertTransaction("markConflict", String.class);
        assertTransaction("recordConflict",
                MemoryScope.class, String.class, String.class, String.class, String.class, String.class);
        assertTransaction("verifyProjectFact", String.class, List.class);
        assertTransaction("recordAudit",
                String.class, String.class, String.class, GovernedMemorySnapshot.class, String.class);
    }

    private void assertTransaction(String method, Class<?>... parameterTypes) throws NoSuchMethodException {
        Transactional annotation = JdbcGovernedMemoryRepository.class
                .getMethod(method, parameterTypes)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals("mysqlTransactionManager", annotation.transactionManager());
    }

    private JdbcGovernedMemoryRepository repository(
            JdbcTemplate jdbc,
            JdbcGovernedMemorySchemaInitializer initializer) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return new JdbcGovernedMemoryRepository(provider, initializer);
    }

    private GovernedMemorySnapshot snapshot() {
        return new GovernedMemorySnapshot(
                0L,
                "memory-1",
                MemoryScope.PROJECT,
                "demo-project",
                "user-1",
                "demo-project",
                "agent-1",
                "session-1",
                MemoryType.PROJECT_FACT,
                "mysql-primary",
                "content",
                "normalized",
                "USER_ASSERTED",
                "run-1",
                false,
                0.8D,
                "LOW",
                "ACTIVE",
                1,
                "hash-1",
                List.of(Map.of("proofId", "proof-1")),
                Instant.parse("2026-07-23T00:00:00Z"),
                "user-1",
                "idempotency-1",
                null,
                null);
    }
}
