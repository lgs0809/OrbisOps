package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSkillEvolutionJobRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void enqueuesIdempotentlyAndReadsTypedJobProjection() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SkillEvolutionJobSnapshot job = job(0);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(job));
        JdbcSkillEvolutionJobRepository repository = new JdbcSkillEvolutionJobRepository(provider);

        SkillEvolutionJobSnapshot stored = repository.enqueue(job);

        assertEquals("job-1", stored.jobId());
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_skill_evolution_job")
                        && sql.contains("USER_EXPLICIT_REMEMBER")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimsPendingJobWithAttemptsAndScheduleCas() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SkillEvolutionJobSnapshot selected = job(1);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.query(argThat(sql -> sql.contains("WHERE status = 'PENDING'")),
                any(RowMapper.class), any(Object[].class))).thenReturn(List.of(selected));
        when(jdbc.update(argThat(sql -> sql.contains("SET status = 'RUNNING'")
                        && sql.contains("AND attempts = ?")
                        && sql.contains("next_run_at")),
                any(Object[].class))).thenReturn(1);
        JdbcSkillEvolutionJobRepository repository = new JdbcSkillEvolutionJobRepository(provider);

        SkillEvolutionJobSnapshot claimed = repository.claimPending(3).orElseThrow();

        assertEquals(1, claimed.attempts());
        verify(jdbc).update(argThat(sql -> sql.contains("SET status = 'RUNNING'")), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistsPatchAndOwnsTerminalAndRetryTransitions() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SkillEvolutionPatchSnapshot patch = patch();
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(patch));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        JdbcSkillEvolutionJobRepository repository = new JdbcSkillEvolutionJobRepository(provider);

        SkillEvolutionPatchSnapshot stored = repository.savePatch(patch);
        boolean completed = repository.complete("job-1", SkillEvolutionJobStatus.COMPLETED);
        boolean retried = repository.rescheduleOrFail(
                "job-1",
                new SkillEvolutionRetryTransition(
                        SkillEvolutionJobStatus.PENDING,
                        2,
                        Instant.parse("2026-07-22T05:01:00Z")),
                "temporary");

        assertEquals("patch-1", stored.patchId());
        assertTrue(completed);
        assertTrue(retried);
        verify(jdbc).update(argThat(sql -> sql.contains("INSERT INTO ai_ops_skill_evolution_patch")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("last_error = NULL")), any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("next_run_at = ?")), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesAvailabilityAndFailsMutationsWhenJdbcIsMissing() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcSkillEvolutionJobRepository repository = new JdbcSkillEvolutionJobRepository(provider);

        assertFalse(repository.available());
        assertEquals("Skill Evolution Job Store 未配置",
                assertThrows(IllegalStateException.class, () -> repository.enqueue(job(0))).getMessage());
    }

    @Test
    void mutationsUseMysqlTransactionManager() throws NoSuchMethodException {
        assertTransaction("enqueue", SkillEvolutionJobSnapshot.class);
        assertTransaction("claimPending", int.class);
        assertTransaction("savePatch", SkillEvolutionPatchSnapshot.class);
        assertTransaction("complete", String.class, SkillEvolutionJobStatus.class);
        assertTransaction("rescheduleOrFail", String.class, SkillEvolutionRetryTransition.class, String.class);
    }

    private void assertTransaction(String method, Class<?>... parameters) throws NoSuchMethodException {
        Transactional annotation = JdbcSkillEvolutionJobRepository.class
                .getMethod(method, parameters)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals("mysqlTransactionManager", annotation.transactionManager());
    }

    private SkillEvolutionJobSnapshot job(int attempts) {
        return new SkillEvolutionJobSnapshot(
                1L,
                "job-1",
                "run-1",
                "session-1",
                "demo-project",
                "agent-1",
                "RUN_COMPLETED",
                SkillEvolutionJobStatus.PENDING,
                attempts,
                Instant.parse("2026-07-22T05:00:00Z"),
                "",
                Instant.parse("2026-07-22T04:00:00Z"),
                Instant.parse("2026-07-22T04:30:00Z"));
    }

    private SkillEvolutionPatchSnapshot patch() {
        return new SkillEvolutionPatchSnapshot(
                1L,
                "patch-1",
                "job-1",
                "run-1",
                "demo-project",
                "skill-1",
                "UPDATE_SKILL_CANDIDATE",
                "{}",
                "{}",
                "CANDIDATE",
                null,
                "",
                Instant.parse("2026-07-22T05:00:00Z"),
                Instant.parse("2026-07-22T05:00:00Z"));
    }
}
