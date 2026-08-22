package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSemanticLexicalRecallAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapsFtsRowsToTypedRankedCandidatesAndPreservesSafeSql() throws Exception {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(resultSet.getString("id")).thenReturn("doc-1");
        when(resultSet.getString("content")).thenReturn("join metric memory");
        when(resultSet.getString("metadata")).thenReturn("""
                {"memory_type":"ops_chat","memory_kind":"message","session_id":"session-1",\
                "user_id":"user-1","memory_status":"ACTIVE"}
                """);
        when(resultSet.getDouble("lexical_rank")).thenReturn(0.8D);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(resultSet, 0));
                });
        OpsSemanticLexicalRecallAdapter adapter = new OpsSemanticLexicalRecallAdapter(
                provider,
                () -> "invalid table;drop");

        List<SemanticMemoryRankedCandidate> result = adapter.recallLexical(
                "session-1", "user-1", "join metric join", 8);

        assertEquals(1, result.size());
        SemanticMemoryRankedCandidate candidate = result.get(0);
        assertEquals("lexical", candidate.source());
        assertEquals(1, candidate.rank());
        assertEquals(1.0D / 61D, candidate.rrfScore(), 0.0000001D);
        assertEquals("doc-1", candidate.document().id());
        assertEquals(0.8D, (Double) candidate.document().metadata().get("memory_lexical_score"), 0.0000001D);
        assertEquals("postgres_fts", candidate.document().metadata().get("memory_lexical_engine"));
        assertEquals(1, candidate.document().metadata().get("memory_lexical_rank"));
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.contains("FROM orbisops_vector_store"),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrongScopeOrInactiveRowsAreExcluded() throws Exception {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(resultSet.getString("id")).thenReturn("doc-1");
        when(resultSet.getString("content")).thenReturn("memory");
        when(resultSet.getString("metadata")).thenReturn("""
                {"memory_type":"ops_chat","memory_kind":"message","session_id":"session-2",\
                "memory_status":"DELETED"}
                """);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    Object mapped = mapper.mapRow(resultSet, 0);
                    return mapped == null ? List.of() : List.of(mapped);
                });
        OpsSemanticLexicalRecallAdapter adapter = new OpsSemanticLexicalRecallAdapter(
                provider,
                () -> "orbisops_vector_store");

        assertTrue(adapter.recallLexical("session-1", "user-1", "memory", 8).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingJdbcOrBlankTokenQueryReturnsEmpty() {
        ObjectProvider<JdbcTemplate> missingProvider = mock(ObjectProvider.class);
        OpsSemanticLexicalRecallAdapter missing = new OpsSemanticLexicalRecallAdapter(
                missingProvider,
                () -> "orbisops_vector_store");

        assertEquals(List.of(), missing.recallLexical("session-1", "user-1", "query", 8));

        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        OpsSemanticLexicalRecallAdapter blank = new OpsSemanticLexicalRecallAdapter(
                provider,
                () -> "orbisops_vector_store");

        assertEquals(List.of(), blank.recallLexical("session-1", "user-1", "a", 8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedMetadataIsSafelyFiltered() throws Exception {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(resultSet.getString("id")).thenReturn("doc-1");
        when(resultSet.getString("content")).thenReturn("memory");
        when(resultSet.getString("metadata")).thenReturn("not-json");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    Object mapped = mapper.mapRow(resultSet, 0);
                    return mapped == null ? List.of() : List.of(mapped);
                });
        OpsSemanticLexicalRecallAdapter adapter = new OpsSemanticLexicalRecallAdapter(
                provider,
                () -> "orbisops_vector_store");

        assertTrue(adapter.recallLexical("session-1", "user-1", "memory", 8).isEmpty());
    }
}
