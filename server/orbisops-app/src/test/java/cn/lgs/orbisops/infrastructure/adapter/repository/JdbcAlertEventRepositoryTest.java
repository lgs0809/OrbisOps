package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertEventDraft;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAlertEventRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcAlertEventRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        repository = new JdbcAlertEventRepository(provider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void appendOwnsGeneratedKeyJsonAndTerminalTimestamp() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        doAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 11L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        doReturn(List.of(snapshot(11L))).when(jdbcTemplate).query(
                anyString(), any(RowMapper.class), any(Object[].class));

        AlertEventSnapshot saved = repository.append(draft(), true);

        assertEquals(11L, saved.id());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture(), eq(Statement.RETURN_GENERATED_KEYS));
        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_alert_trigger_event"));
        verify(statement).setString(17, "{\"env\":\"prod\"}");
        verify(statement).setString(18, "{\"summary\":\"slow\"}");
        verify(statement).setString(19, "{\"status\":\"firing\"}");
        verify(statement).setObject(eq(15), any(java.sql.Timestamp.class));
    }

    @Test
    void runOutcomeUsesExplicitIntegerTerminalFlagAndRunIdFilter() {
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        AlertRunOutcome outcome = new AlertRunOutcome(
                "ALERTMANAGER", "fp-1", "run-1", "SUCCEEDED", "done", "");

        repository.updateRunOutcome(outcome, true);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("CASE WHEN ? = 1 THEN CURRENT_TIMESTAMP"));
        assertTrue(sql.getValue().contains("(?='' OR run_id=?)"));
        assertEquals(1, arguments.getValue()[3]);
        assertEquals("ALERTMANAGER", arguments.getValue()[4]);
        assertEquals("fp-1", arguments.getValue()[5]);
        assertEquals("run-1", arguments.getValue()[6]);
        assertEquals("run-1", arguments.getValue()[7]);
    }

    @Test
    void blankRunIdKeepsFingerprintWideCompatibilitySemantics() {
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

        repository.updateRunOutcome(new AlertRunOutcome(
                "ALERTMANAGER", "fp-1", "", "FAILED", "", "error"), true);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), arguments.capture());
        assertEquals("", arguments.getValue()[6]);
        assertEquals("", arguments.getValue()[7]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsesFixedDescendingIdAndBoundedLimitArgument() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(RowMapper.class), any(Object[].class));

        repository.list(50);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertTrue(sql.getValue().contains("FROM ai_ops_alert_trigger_event"));
        assertTrue(sql.getValue().contains("ORDER BY id DESC LIMIT ?"));
        assertEquals(50, arguments.getValue()[0]);
    }

    private AlertEventDraft draft() {
        return new AlertEventDraft(
                7L, "Payment Alert", "project-1", "ALERTMANAGER", "TRIGGERED",
                "dispatch-1", "fp-1", "HighErrorRate", "critical", "payment", "ops",
                "run-1", "SUCCEEDED", "done", "", Map.of("env", "prod"),
                Map.of("summary", "slow"), Map.of("status", "firing"));
    }

    private AlertEventSnapshot snapshot(Long id) {
        AlertEventDraft draft = draft();
        return new AlertEventSnapshot(
                id, draft.ruleId(), draft.ruleName(), draft.projectId(), draft.sourceType(), draft.status(),
                draft.dispatchKey(), draft.fingerprint(), draft.alertName(), draft.severity(), draft.serviceName(),
                draft.receiver(), draft.runId(), draft.runStatus(), draft.finalSummary(), "completed",
                draft.errorMessage(), draft.labels(), draft.annotations(), draft.payload(), "created");
    }
}
