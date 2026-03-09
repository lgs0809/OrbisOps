package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcColdMemoryRepositoryTest {

    @Test
    void appendMessagePersistsTypedSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcColdMemoryRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.appendMessage(new ColdMemoryMessageSnapshot(
                "session-1", "user-1", "user", "hello", "", Map.of("turn", 1)));

        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_chat_message")),
                any(Object[].class));
    }

    @Test
    void saveItemsPersistsTypedMemoryRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcColdMemoryRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.saveItems(List.of(item()));

        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_memory_item")),
                any(Object[].class));
    }

    @Test
    void clearDeletesMessageAndMemoryRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcColdMemoryRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.clear("session-1");

        verify(jdbc).update(argThat(sql -> sql.contains("DELETE FROM ai_ops_chat_message")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("DELETE FROM ai_ops_memory_item")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingStoreFailsClosed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcColdMemoryRepository repository = new JdbcColdMemoryRepository(provider, null);

        assertThrows(IllegalStateException.class,
                () -> repository.appendMessage(new ColdMemoryMessageSnapshot(
                        "session-1", "user-1", "user", "hello", "", Map.of())));
    }

    @Test
    void writeMethodsDeclareMysqlTransactionBoundary() throws NoSuchMethodException {
        Transactional append = JdbcColdMemoryRepository.class
                .getMethod("appendMessage", ColdMemoryMessageSnapshot.class)
                .getAnnotation(Transactional.class);
        Transactional saveItems = JdbcColdMemoryRepository.class
                .getMethod("saveItems", List.class)
                .getAnnotation(Transactional.class);
        Transactional clear = JdbcColdMemoryRepository.class
                .getMethod("clear", String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(append);
        assertNotNull(saveItems);
        assertNotNull(clear);
        assertEquals("mysqlTransactionManager", append.transactionManager());
        assertEquals("mysqlTransactionManager", saveItems.transactionManager());
        assertEquals("mysqlTransactionManager", clear.transactionManager());
    }

    private JdbcColdMemoryRepository repository(JdbcTemplate jdbc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return new JdbcColdMemoryRepository(provider, null);
    }

    private ColdMemoryItemSnapshot item() {
        return new ColdMemoryItemSnapshot(
                "session-1",
                "user-1",
                "fact",
                "memory",
                BigDecimal.valueOf(0.8),
                "[]",
                "user",
                "a".repeat(64),
                Map.of("turn", 1),
                "");
    }
}
