package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAnalysisAuditRepositoryTest {

    @Test
    void upsertOwnsFixedSqlAndTypedJson() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = provider(jdbc);
        JdbcAnalysisAuditRepository repository = new JdbcAnalysisAuditRepository(provider, true);
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));

        repository.upsert(record("analysis-1"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_agent_audit"));
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        assertEquals("analysis-1", args.getValue()[0]);
        assertEquals(1, args.getValue()[1]);
        assertEquals("[\"ES\"]", args.getValue()[8]);
        assertEquals("{\"ES\":\"FOUND\"}", args.getValue()[11]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listOwnsDescendingLimitQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = provider(jdbc);
        JdbcAnalysisAuditRepository repository = new JdbcAnalysisAuditRepository(provider, true);
        doReturn(List.of(record("analysis-1"))).when(jdbc).query(
                anyString(), any(RowMapper.class), any(Object[].class));

        List<AnalysisAuditRecord> rows = repository.list(20);

        assertEquals("analysis-1", rows.get(0).analysisId());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("FROM ai_ops_agent_audit"));
        assertTrue(sql.getValue().contains("ORDER BY id DESC"));
        assertEquals(20, args.getValue()[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rowMapperAcceptsGenericNumberTypes() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("analysis_id")).thenReturn("analysis-1");
        when(rs.getInt("success")).thenReturn(1);
        when(rs.getString("question")).thenReturn("question");
        when(rs.getString("intent")).thenReturn("intent");
        when(rs.getObject("range_minutes")).thenReturn(Short.valueOf((short) 15));
        when(rs.getString("prom_window")).thenReturn("5m");
        when(rs.getString("generated_at")).thenReturn("now");
        when(rs.getObject("duration_ms")).thenReturn(Integer.valueOf(10));
        when(rs.getString("selected_sources_json")).thenReturn("[\"ES\"]");
        when(rs.getString("executed_sources_json")).thenReturn("[\"ES\"]");
        when(rs.getString("skipped_sources_json")).thenReturn("[]");
        when(rs.getString("result_statuses_json")).thenReturn("{\"ES\":\"FOUND\"}");
        when(rs.getString("insight_levels_json")).thenReturn("[\"WARN\"]");
        when(rs.getString("conclusion")).thenReturn("done");
        when(rs.getString("error_message")).thenReturn("");
        doAnswer(invocation -> {
            RowMapper<AnalysisAuditRecord> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        JdbcAnalysisAuditRepository repository = new JdbcAnalysisAuditRepository(provider(jdbc), true);

        AnalysisAuditRecord row = repository.list(20).get(0);

        assertEquals(15, row.rangeMinutes());
        assertEquals(10L, row.durationMs());
        assertEquals(Map.of("ES", "FOUND"), row.resultStatuses());
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedHistoricalJsonFallsBackToEmptyPersistentView() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("analysis_id")).thenReturn("analysis-1");
        when(rs.getInt("success")).thenReturn(1);
        when(rs.getObject("range_minutes")).thenReturn(15);
        when(rs.getObject("duration_ms")).thenReturn(10L);
        when(rs.getString("selected_sources_json")).thenReturn("not-json");
        doAnswer(invocation -> {
            RowMapper<AnalysisAuditRecord> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        JdbcAnalysisAuditRepository repository = new JdbcAnalysisAuditRepository(provider(jdbc), true);

        assertTrue(repository.list(20).isEmpty());
    }

    @Test
    void disabledOrUnavailableJdbcReturnsEmptyWithoutWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAnalysisAuditRepository disabled = new JdbcAnalysisAuditRepository(provider(jdbc), false);
        JdbcAnalysisAuditRepository unavailable = new JdbcAnalysisAuditRepository(provider(null), true);

        disabled.upsert(record("analysis-1"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        assertTrue(disabled.list(20).isEmpty());
        assertTrue(unavailable.list(20).isEmpty());
    }

    @Test
    void dataAccessFailureFallsBackToEmptyPersistentView() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAnalysisAuditRepository repository = new JdbcAnalysisAuditRepository(provider(jdbc), true);
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        assertTrue(repository.list(20).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbc) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return provider;
    }

    private AnalysisAuditRecord record(String id) {
        return new AnalysisAuditRecord(
                id, true, "question", "intent", 15, "5m", "now", 10L,
                List.of("ES"), List.of("ES"), List.of("PROM"), Map.of("ES", "FOUND"),
                List.of("WARN"), "conclusion", "");
    }
}
