package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogMutation;
import cn.lgs.orbisops.domain.skill.model.SkillCurrentPointerUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcSkillCatalogRepositoryTest {

    @Test
    void insertIfAbsentReturnsAffectedRowResult() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcSkillCatalogRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertTrue(repository.insertIfAbsent(entry()));
        assertFalse(repository.insertIfAbsent(entry()));
    }

    @Test
    void ordinaryMutationReturnsAffectedRowCasResult() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcSkillCatalogRepository repository = repository(jdbc);
        SkillCatalogMutation mutation = mutation();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

        assertTrue(repository.compareAndSetMutation(mutation));
        assertFalse(repository.compareAndSetMutation(mutation));
    }

    @Test
    void evolutionPublishReturnsAffectedRowCasResult() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcSkillCatalogRepository repository = repository(jdbc);
        SkillEvolutionUpdate update = evolutionUpdate();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

        assertTrue(repository.compareAndSetEvolution(update));
        assertFalse(repository.compareAndSetEvolution(update));
    }

    @Test
    void currentPointerUpdateReturnsAffectedRowCasResult() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcSkillCatalogRepository repository = repository(jdbc);
        SkillCurrentPointerUpdate update = currentPointerUpdate();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

        assertTrue(repository.compareAndSetCurrent(update));
        assertFalse(repository.compareAndSetCurrent(update));
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        JdbcSkillCatalogRepository repository = new JdbcSkillCatalogRepository();

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.insertIfAbsent(entry()));
        assertThrows(IllegalStateException.class, () -> repository.compareAndSetMutation(mutation()));
        assertThrows(IllegalStateException.class,
                () -> repository.compareAndSetEvolution(evolutionUpdate()));
        assertThrows(IllegalStateException.class,
                () -> repository.compareAndSetCurrent(currentPointerUpdate()));
    }

    private JdbcSkillCatalogRepository repository(JdbcTemplate jdbcTemplate) {
        JdbcSkillCatalogRepository repository = new JdbcSkillCatalogRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
        return repository;
    }

    private SkillCatalogEntry entry() {
        return new SkillCatalogEntry(0, "diagnosis", "project-a", "Diagnosis", "PROJECT", "",
                "description", "# skill", 3, "ACTIVE", "system", null, null,
                "EVOLVED", "AUTO", true, true, LocalDateTime.parse("2026-07-17T00:00:00"),
                "", "", null, "skill-hash-3", 3, "skill-hash-3", 3,
                "package-hash-3", "{\"kind\":\"SkillPackage\"}", "{\"SKILL.md\":\"hash\"}");
    }

    private SkillCatalogMutation mutation() {
        return new SkillCatalogMutation(
                "PROJECT", "project-a", "diagnosis", 3, "skill-hash-3", 4,
                "skill-hash-4", "Diagnosis", "", "description v4", "# skill v4",
                "ACTIVE", "MANUAL", "AUTO", true, true,
                LocalDateTime.parse("2026-07-18T00:00:00"), "", "", null,
                "package-hash-4", "{\"kind\":\"SkillPackage\"}",
                "{\"SKILL.md\":\"hash-4\"}");
    }

    private SkillCurrentPointerUpdate currentPointerUpdate() {
        return new SkillCurrentPointerUpdate("GLOBAL", "", "diagnosis", 3, "skill-hash-3", 4,
                "skill-hash-4", "Diagnosis", "description", "# skill v4", "MANUAL",
                "package-hash-4", "{\"kind\":\"SkillPackage\"}",
                "{\"SKILL.md\":\"hash-4\"}");
    }

    private SkillEvolutionUpdate evolutionUpdate() {
        return new SkillEvolutionUpdate("project-a", "diagnosis", 3, "skill-hash-3", 4,
                "skill-hash-4", "Diagnosis", "description", "# skill v4", "package-hash-4",
                "{\"kind\":\"SkillPackage\"}", "{\"SKILL.md\":\"hash-4\"}");
    }
}
