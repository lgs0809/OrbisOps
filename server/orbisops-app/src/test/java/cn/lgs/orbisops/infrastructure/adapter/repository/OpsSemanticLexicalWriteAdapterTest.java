package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSemanticLexicalWriteAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void persistsTypedDocumentAsJsonbWithSafeTableFallback() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        OpsSemanticLexicalWriteAdapter adapter = new OpsSemanticLexicalWriteAdapter(
                provider,
                () -> "unsafe table;drop");
        SemanticMemoryDocumentSnapshot snapshot = new SemanticMemoryDocumentSnapshot(
                "",
                "semantic memory",
                Map.of(
                        "memory_type", "ops_chat",
                        "session_id", "session-1"));

        adapter.writeLexical(snapshot);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), contentCaptor.capture(), metadataCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("INSERT INTO vector_store_qwen_vl_2048"));
        assertTrue(sqlCaptor.getValue().contains("?::jsonb"));
        assertEquals("semantic memory", contentCaptor.getValue());
        assertTrue(metadataCaptor.getValue().contains("\"memory_type\":\"ops_chat\""));
        assertTrue(metadataCaptor.getValue().contains("\"session_id\":\"session-1\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingJdbcFailsForApplicationContainment() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        OpsSemanticLexicalWriteAdapter adapter = new OpsSemanticLexicalWriteAdapter(
                provider,
                () -> "vector_store_qwen_vl_2048");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.writeLexical(new SemanticMemoryDocumentSnapshot(
                        "", "memory", Map.of())));

        assertEquals("PgVector JdbcTemplate 未初始化", error.getMessage());
    }
}
