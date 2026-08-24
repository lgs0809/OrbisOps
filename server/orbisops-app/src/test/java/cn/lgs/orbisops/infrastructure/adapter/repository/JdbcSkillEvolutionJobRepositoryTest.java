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
        assertTransaction("renewLease", SkillEvolutionJobSnapshot.class);
        assertTransaction("complete", SkillEvolutionJobSnapshot.class, SkillEvolutionPatchSnapshot.class, SkillEvolutionJobStatus.class);
        assertTransaction("rescheduleOrFail", SkillEvolutionJobSnapshot.class, SkillEvolutionRetryTransition.class, String.class);
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
