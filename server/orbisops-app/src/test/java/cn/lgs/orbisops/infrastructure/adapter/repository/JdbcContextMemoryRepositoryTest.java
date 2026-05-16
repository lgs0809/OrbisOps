package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcContextMemoryRepositoryTest {

    @Test
    void upsertPersistsTypedSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcContextMemorySchemaInitializer initializer = mock(JdbcContextMemorySchemaInitializer.class);
        JdbcContextMemoryRepository repository = repository(jdbc, initializer);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.upsert(snapshot());

        verify(initializer).initialize();
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_context_memory")),
                any(Object[].class));
    }

    @Test
    void updateStatusReturnsMutationOutcome() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcContextMemoryRepository repository = repository(jdbc, null);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        boolean updated = repository.updateStatus("ctx-1", "ARCHIVED");

        assertEquals(true, updated);
        verify(jdbc).update(argThat(sql -> sql.contains("UPDATE ai_ops_context_memory")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingStoreFailsClosed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcContextMemoryRepository repository = new JdbcContextMemoryRepository(provider, null);

        assertThrows(IllegalStateException.class, () -> repository.upsert(snapshot()));
    }

    @Test
    void mutationMethodsDeclareMysqlTransactionBoundary() throws NoSuchMethodException {
        Transactional upsert = JdbcContextMemoryRepository.class
                .getMethod("upsert", ContextMemorySnapshot.class)
                .getAnnotation(Transactional.class);
        Transactional updateStatus = JdbcContextMemoryRepository.class
                .getMethod("updateStatus", String.class, String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(upsert);
        assertNotNull(updateStatus);
        assertEquals("mysqlTransactionManager", upsert.transactionManager());
        assertEquals("mysqlTransactionManager", updateStatus.transactionManager());
    }

    private JdbcContextMemoryRepository repository(JdbcTemplate jdbc,
                                                    JdbcContextMemorySchemaInitializer initializer) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return new JdbcContextMemoryRepository(provider, initializer);
    }

    private ContextMemorySnapshot snapshot() {
        return new ContextMemorySnapshot(
                null,
                "ctx-1",
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "DDD migration",
                "summary",
                "content",
                "[]",
                "ACTIVE",
                BigDecimal.valueOf(0.9D),
                "memory_extractor",
                "session-1",
                "source-hash",
                "user-1",
                "",
                "",
                "");
    }
}
