package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSkillEvolutionSignalRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void savesSignalIdempotentlyAndReadsStoredProjection() throws Exception {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(row.getString("signal_id")).thenReturn("signal-existing");
        when(row.getString("idempotency_key")).thenReturn("idem-1");
        when(row.getString("project_id")).thenReturn("demo-project");
        when(row.getString("agent_id")).thenReturn("agent-1");
        when(row.getString("run_id")).thenReturn("run-1");
        when(row.getString("session_id")).thenReturn("session-1");
        when(row.getString("signal_type")).thenReturn("USER_ASSERTED_PROCEDURE");
        when(row.getString("payload_json")).thenReturn("{}");
        when(row.getString("status")).thenReturn("CREATED");
        when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-07-22T01:00:00Z")));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    org.springframework.jdbc.core.RowMapper<SkillEvolutionSignalSnapshot> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
        JdbcSkillEvolutionSignalRepository repository = new JdbcSkillEvolutionSignalRepository(provider);

        SkillEvolutionSignalSnapshot stored = repository.saveIdempotent(signal());

        assertEquals("signal-existing", stored.signalId());
        assertEquals("idem-1", stored.idempotencyKey());
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_skill_evolution_signal")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistsHintsQueriesPendingAndMarksConsumed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcSkillEvolutionSignalRepository repository = new JdbcSkillEvolutionSignalRepository(provider);

        repository.saveHintIdempotent(hint());
        assertEquals(true, repository.markHintConsumed("hint-1"));

        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_skill_evolution_hint")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("SET status='CONSUMED'")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingJdbcFailsWithHistoricalMessages() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcSkillEvolutionSignalRepository repository = new JdbcSkillEvolutionSignalRepository(provider);

        assertEquals("Skill Evolution Signal Store 未配置",
                assertThrows(IllegalStateException.class,
                        () -> repository.saveIdempotent(signal())).getMessage());
        assertEquals("Skill Evolution Hint Store 未配置",
                assertThrows(IllegalStateException.class,
                        () -> repository.saveHintIdempotent(hint())).getMessage());
    }

    @Test
    void mutationsUseMysqlTransactionManager() throws NoSuchMethodException {
        assertTransaction("saveIdempotent", SkillEvolutionSignalSnapshot.class);
        assertTransaction("saveHintIdempotent", SkillEvolutionHintSnapshot.class);
        assertTransaction("markHintConsumed", String.class);
    }

    private void assertTransaction(String method, Class<?>... parameters) throws NoSuchMethodException {
        Transactional annotation = JdbcSkillEvolutionSignalRepository.class
                .getMethod(method, parameters)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals("mysqlTransactionManager", annotation.transactionManager());
    }

    private SkillEvolutionSignalSnapshot signal() {
        return new SkillEvolutionSignalSnapshot(
                "signal-new", "idem-1", "demo-project", "agent-1", "run-1", "session-1",
                "USER_ASSERTED_PROCEDURE", "{}", "CREATED", null);
    }

    private SkillEvolutionHintSnapshot hint() {
        return new SkillEvolutionHintSnapshot(
                "hint-1", "signal-1", "demo-project", "run-1",
                "USER_ASSERTED_PROCEDURE", "{}", "CREATED", null);
    }
}
