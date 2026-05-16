package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAlertRuleRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcAlertRuleRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        repository = new JdbcAlertRuleRepository(provider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertOwnsFixedSqlGeneratedKeyAndTypedLabelsJson() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        doAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 9L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        doReturn(List.of(definition(9L))).when(jdbcTemplate).query(
                anyString(), any(RowMapper.class), any(Object[].class));

        AlertRuleDefinition saved = repository.save(definition(null));

        assertEquals(9L, saved.id());
        ArgumentCaptor<String> insertSql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(insertSql.capture(), eq(Statement.RETURN_GENERATED_KEYS));
        assertTrue(insertSql.getValue().contains("INSERT INTO ai_ops_alert_trigger_rule"));
        assertTrue(insertSql.getValue().contains("agent_definition_hash"));
        verify(statement).setString(7, "{\"env\":\"prod\"}");
        verify(statement).setObject(14, 4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateUsesFixedSqlAndReadsBackSavedDefinition() {
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        doReturn(List.of(definition(7L))).when(jdbcTemplate).query(
                anyString(), any(RowMapper.class), any(Object[].class));

        AlertRuleDefinition saved = repository.save(definition(7L));

        assertEquals(7L, saved.id());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("UPDATE ai_ops_alert_trigger_rule"));
        assertTrue(sql.getValue().contains("WHERE id=?"));
        assertEquals("{\"env\":\"prod\"}", arguments.getValue()[6]);
        assertEquals(7L, arguments.getValue()[24]);
    }

    @Test
    void statusAndDeleteOwnTheirMutationSql() {
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));

        assertTrue(repository.updateStatus(7L, 0));
        assertTrue(repository.delete(7L));

        verify(jdbcTemplate).update(
                eq("UPDATE ai_ops_alert_trigger_rule SET status=? WHERE id=?"),
                eq(0), eq(7L));
        verify(jdbcTemplate).update(
                eq("DELETE FROM ai_ops_alert_trigger_rule WHERE id=?"),
                eq(7L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsClosedWithoutMysqlJdbcTemplate() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcAlertRuleRepository unavailable = new JdbcAlertRuleRepository(provider);

        assertEquals("ALERT_RULE_STORE_UNAVAILABLE",
                assertThrows(IllegalStateException.class, unavailable::list).getMessage());
    }

    private AlertRuleDefinition definition(Long id) {
        return new AlertRuleDefinition(
                id, "Payment Alert", 1, "ALERTMANAGER", "High.*", "critical", "payment",
                Map.of("env", "prod"), "channel-1", "ops", "secret", "project-1", "agent-1",
                "LATEST_PUBLISHED", 4, "agent-v4", "question", 30, "5m", true, true,
                5, 120, 20, 300, "created", "updated");
    }
}
