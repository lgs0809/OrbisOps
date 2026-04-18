package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.LandingOperationPayload;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcLandingOperationJournalAdapterTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void expiredUnknownUsesExistingTableColumnsAndStableRecoveryOrdering() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        JdbcLandingOperationJournalAdapter adapter = new JdbcLandingOperationJournalAdapter(jdbc);

        assertTrue(adapter.expiredUnknown(500).isEmpty());

        verify(jdbc).query(
                argThat(sql -> sql.contains("fact_status='UNKNOWN'")
                        && sql.contains("ORDER BY create_time ASC, id ASC")
                        && !sql.contains("update_time")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    void toolExecutionCompletionProjectsAuthoritativeExecutionIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(
                argThat(sql -> sql != null
                        && sql.contains("TOOL_EXECUTION_AUTHORITATIVE_COMPLETION")
                        && sql.contains("WHERE landing_run_id=? AND operation_id=? AND tool_name=?")
                        && sql.contains("AND execution_key=?")
                        && !sql.contains("SET execution_key=?")
                        && sql.contains("fact_status='COMPLETED'")),
                any(Object[].class))).thenReturn(1);
        JdbcLandingOperationJournalAdapter adapter = new JdbcLandingOperationJournalAdapter(jdbc);

        boolean completed = adapter.completeFromToolExecution(
                "landing-run-1",
                "restart-service",
                "restart_service",
                "execution-1",
                new LandingOperationPayload(
                        Map.of("status", "SUCCEEDED"),
                        "remote-1",
                        "result-1",
                        "a".repeat(64)));

        assertTrue(completed);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exposesStableExecutionBindingsForTheCurrentLandingAttempt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        JdbcLandingOperationJournalAdapter adapter = new JdbcLandingOperationJournalAdapter(jdbc);

        assertTrue(adapter.operationExecutionBindings("landing-run-1").isEmpty());

        verify(jdbc).query(
                argThat(sql -> sql.contains("operation_id, execution_key, toolset_id, tool_name, resource_key")
                        && sql.contains("WHERE landing_run_id=?")
                        && sql.contains("ORDER BY id ASC")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    void repeatedSameToolExecutionCompletionIsIdempotent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(
                argThat(sql -> sql != null
                        && sql.contains("execution_key=?")
                        && sql.contains("fact_status='COMPLETED'")
                        && sql.contains("result_id=?")
                        && sql.contains("output_hash=?")),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                any(Object[].class))).thenReturn(1);
        JdbcLandingOperationJournalAdapter adapter = new JdbcLandingOperationJournalAdapter(jdbc);

        assertTrue(adapter.completeFromToolExecution(
                "landing-run-1",
                "restart-service",
                "restart_service",
                "execution-1",
                new LandingOperationPayload(Map.of(), "remote-1", "result-1", "a".repeat(64))));
    }
}
