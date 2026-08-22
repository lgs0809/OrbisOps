package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSemanticMemoryClearPersistenceAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void clearsSessionWithSafeTableFallbackAndBoundParameter() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        OpsSemanticMemoryClearPersistenceAdapter adapter =
                new OpsSemanticMemoryClearPersistenceAdapter(
                        provider,
                        () -> "unsafe table;drop");

        boolean cleared = adapter.clearSession("session-1");

        assertTrue(cleared);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), sessionCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("orbisops_vector_store"));
        assertTrue(sqlCaptor.getValue().contains("metadata->>'memory_type' = 'ops_chat'"));
        assertTrue(sqlCaptor.getValue().contains("metadata->>'session_id' = ?"));
        assertEquals("session-1", sessionCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingJdbcReturnsFalseWithoutThrowing() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        OpsSemanticMemoryClearPersistenceAdapter adapter =
                new OpsSemanticMemoryClearPersistenceAdapter(
                        provider,
                        () -> "orbisops_vector_store");

        assertFalse(adapter.clearSession("session-1"));
    }
}
