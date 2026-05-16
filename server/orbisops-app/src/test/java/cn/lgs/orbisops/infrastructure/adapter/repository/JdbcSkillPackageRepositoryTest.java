package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSkillPackageRepositoryTest {

    @Test
    void appendWritesVersionAndArtifactsAsOneRepositoryOperation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcSkillPackageRepository repository = spy(repository(jdbcTemplate));
        SkillPackageVersion version = version("package-hash");
        List<SkillArtifact> artifacts = artifacts();
        doReturn(Optional.empty()).when(repository).findVersion(version.key());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.appendVersion(version, artifacts);

        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    void exactRetryIsIdempotentAndDoesNotWriteAgain() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcSkillPackageRepository repository = spy(repository(jdbcTemplate));
        SkillPackageVersion version = version("package-hash");
        List<SkillArtifact> artifacts = artifacts();
        doReturn(Optional.of(version)).when(repository).findVersion(version.key());
        doReturn(artifacts).when(repository).findArtifacts(version.key());

        assertDoesNotThrow(() -> repository.appendVersion(version, artifacts));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void sameVersionWithDifferentImmutableContentFailsClosed() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcSkillPackageRepository repository = spy(repository(jdbcTemplate));
        SkillPackageVersion requested = version("new-package-hash");
        doReturn(Optional.of(version("stored-package-hash"))).when(repository).findVersion(requested.key());
        doReturn(artifacts()).when(repository).findArtifacts(requested.key());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository.appendVersion(requested, artifacts()));

        assertTrue(error.getMessage().contains("IMMUTABLE_CONFLICT"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void incompleteVersionCannotBePersisted() {
        JdbcSkillPackageRepository repository = repository(mock(JdbcTemplate.class));
        SkillPackageVersion incomplete = new SkillPackageVersion(0, key(), "", 0, "", "", "", "", "",
                "# skill", "MANUAL", "", "", "", "", "", "SKILL.md", 2, 10, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> repository.appendVersion(incomplete, artifacts()));

        assertTrue(error.getMessage().contains("INCOMPLETE"));
    }

    @SuppressWarnings("unchecked")
    private JdbcSkillPackageRepository repository(JdbcTemplate jdbcTemplate) {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        return new JdbcSkillPackageRepository(provider);
    }

    private SkillPackageVersion version(String packageHash) {
        return new SkillPackageVersion(0, key(), "skill-hash", 1, "base-hash", "run-1", "session-1",
                "job-1", "AUTO_EVOLVER", "# skill", "SKILL_EVOLVER", "trace-1", "update",
                packageHash, "{\"kind\":\"SkillPackage\"}", "{\"SKILL.md\":\"hash-1\"}",
                "SKILL.md", 2, 18, Instant.parse("2026-07-16T00:00:00Z"));
    }

    private SkillPackageKey key() {
        return new SkillPackageKey("PROJECT", "project-a", "diagnosis", 2);
    }

    private List<SkillArtifact> artifacts() {
        return List.of(
                new SkillArtifact("SKILL.md", "ENTRYPOINT", "text/markdown", "UTF8", "hash-1", 7, "# skill"),
                new SkillArtifact("resources/query.yml", "RESOURCE", "application/yaml", "UTF8", "hash-2", 11, "query: up\n"));
    }
}
