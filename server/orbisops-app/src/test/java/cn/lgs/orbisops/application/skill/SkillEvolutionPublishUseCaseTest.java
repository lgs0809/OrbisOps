package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillEvolutionPublishUseCaseTest {

    @Test
    void publishUsesCasAndAppendsNextImmutablePackageVersion() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry before = current("ACTIVE", "AUTO", true, 3, "hash-3");
        SkillCatalogEntry after = current("ACTIVE", "AUTO", true, 4, "hash-4");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "demo-project", "slow-sql", true))
                .thenReturn(Optional.of(before), Optional.of(after));
        when(packageRepository.findArtifacts(before.key())).thenReturn(currentArtifacts());
        when(catalogRepository.compareAndSetEvolution(any())).thenReturn(true);
        SkillEvolutionPublishUseCase useCase = useCase(catalogRepository, packageRepository);

        SkillEvolutionPublishOutcome outcome = useCase.publishProject(
                "demo-project", "Slow SQL",
                Map.of(
                        "content", "new content",
                        "sourceRunId", "run-1",
                        "sourceSessionId", "session-1",
                        "evolutionJobId", "job-1",
                        "changeSummary", "auto evolve"),
                3, "hash-3", "SYSTEM_SKILL_EVOLVER");

        assertTrue(outcome.published());
        assertEquals("PUBLISHED", outcome.reasonCode());
        ArgumentCaptor<SkillEvolutionUpdate> update = ArgumentCaptor.forClass(SkillEvolutionUpdate.class);
        verify(catalogRepository).compareAndSetEvolution(update.capture());
        assertEquals(3, update.getValue().baseVersion());
        assertEquals("hash-3", update.getValue().baseSkillHash());
        assertEquals(4, update.getValue().nextVersion());
        assertFalse(update.getValue().packageHash().isBlank());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SkillArtifact>> artifacts = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<SkillPackageVersion> version = ArgumentCaptor.forClass(SkillPackageVersion.class);
        verify(packageRepository).appendVersion(version.capture(), artifacts.capture());
        assertEquals("AUTO_EVOLVER", version.getValue().publishMode());
        assertEquals("SKILL_EVOLVER", version.getValue().sourceType());
        assertEquals("run-1", version.getValue().sourceTraceId());
        assertEquals("session-1", version.getValue().sourceSessionId());
        assertEquals("job-1", version.getValue().evolutionJobId());
        assertTrue(artifacts.getValue().stream()
                .anyMatch(item -> "resources/query.yaml".equals(item.path())));
        assertTrue(version.getValue().manifestJson().contains("database.read"));
    }

    @Test
    void explicitEvolutionArtifactsReplaceCurrentPackageFiles() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry before = current("ACTIVE", "AUTO", true, 3, "hash-3");
        SkillCatalogEntry after = current("ACTIVE", "AUTO", true, 4, "hash-4");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "demo-project", "slow-sql", true))
                .thenReturn(Optional.of(before), Optional.of(after));
        when(packageRepository.findArtifacts(before.key())).thenReturn(currentArtifacts());
        when(catalogRepository.compareAndSetEvolution(any())).thenReturn(true);

        useCase(catalogRepository, packageRepository).publishProject(
                "demo-project", "slow-sql",
                Map.of(
                        "content", "new content",
                        "artifacts", List.of(
                                Map.of("path", "resources/new.yaml", "role", "RESOURCE", "content", "query: new\n"),
                                Map.of("path", "evals/new.json", "role", "EVAL", "content", "[]")),
                        "evalSuites", List.of("evals/new.json")),
                3, "hash-3", "SYSTEM_SKILL_EVOLVER");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SkillArtifact>> artifacts = ArgumentCaptor.forClass(List.class);
        verify(packageRepository).appendVersion(any(), artifacts.capture());
        assertTrue(artifacts.getValue().stream()
                .anyMatch(item -> "resources/new.yaml".equals(item.path())));
        assertTrue(artifacts.getValue().stream()
                .noneMatch(item -> "resources/query.yaml".equals(item.path())));
        assertTrue(artifacts.getValue().stream()
                .anyMatch(item -> "evals/new.json".equals(item.path())));
    }

    @Test
    void manualAndInactiveSkillsReturnStableSkipReasons() {
        for (String status : List.of("PAUSED", "DEPRECATED", "REJECTED", "DISABLED")) {
            ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
            ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
            when(catalogRepository.available()).thenReturn(true);
            when(packageRepository.available()).thenReturn(true);
            when(catalogRepository.find("PROJECT", "demo-project", "slow-sql", true))
                    .thenReturn(Optional.of(current(status, "AUTO", true, 3, "hash-3")));

            SkillEvolutionPublishOutcome outcome = useCase(catalogRepository, packageRepository)
                    .publishProject("demo-project", "slow-sql", Map.of("content", "new"),
                            3, "hash-3", "SYSTEM_SKILL_EVOLVER");

            assertFalse(outcome.published());
            assertEquals("STATUS_SKIP", outcome.reasonCode());
            verify(packageRepository, never()).findArtifacts(any());
        }

        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "demo-project", "slow-sql", true))
                .thenReturn(Optional.of(current("ACTIVE", "MANUAL_ONLY", true, 3, "hash-3")));

        SkillEvolutionPublishOutcome manual = useCase(catalogRepository, packageRepository)
                .publishProject("demo-project", "slow-sql", Map.of("content", "new"),
                        3, "hash-3", "SYSTEM_SKILL_EVOLVER");

        assertFalse(manual.published());
        assertEquals("MANUAL_ONLY_SKIP", manual.reasonCode());
    }

    @Test
    void casConflictReloadsCurrentStateAndReturnsMvccReason() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry before = current("ACTIVE", "AUTO", true, 3, "hash-3");
        SkillCatalogEntry reloaded = current("ACTIVE", "AUTO", true, 4, "hash-4");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "demo-project", "slow-sql", true))
                .thenReturn(Optional.of(before), Optional.of(reloaded));
        when(packageRepository.findArtifacts(before.key())).thenReturn(currentArtifacts());
        when(catalogRepository.compareAndSetEvolution(any())).thenReturn(false);

        SkillEvolutionPublishOutcome outcome = useCase(catalogRepository, packageRepository)
                .publishProject("demo-project", "slow-sql", Map.of("content", "new"),
                        3, "hash-3", "SYSTEM_SKILL_EVOLVER");

        assertFalse(outcome.published());
        assertEquals("MVCC_CONFLICT", outcome.reasonCode());
        assertEquals(4, outcome.current().currentVersion());
        verify(packageRepository, never()).appendVersion(any(), any());
    }

    @Test
    void frozenOrStaleBaseFailsBeforePackageMutation() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "demo-project", "slow-sql", true))
                .thenReturn(Optional.of(current("FROZEN", "AUTO", true, 3, "hash-3")));
        SkillEvolutionPublishUseCase useCase = useCase(catalogRepository, packageRepository);

        SkillEvolutionPublishOutcome frozen = useCase.publishProject(
                "demo-project", "slow-sql", Map.of("content", "new"),
                3, "hash-3", "SYSTEM_SKILL_EVOLVER");
        assertFalse(frozen.published());
        assertEquals("LEGACY_FROZEN_CLASSIFICATION_REQUIRED", frozen.reasonCode());
        verify(packageRepository, never()).findArtifacts(any());

        ISkillCatalogRepository staleCatalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository stalePackages = mock(ISkillPackageRepository.class);
        when(staleCatalog.available()).thenReturn(true);
        when(stalePackages.available()).thenReturn(true);
        when(staleCatalog.find("PROJECT", "demo-project", "slow-sql", true))
                .thenReturn(Optional.of(current("ACTIVE", "AUTO", true, 4, "hash-4")));

        assertEquals("SKILL_EVOLUTION_BASE_VERSION_CONFLICT",
                assertThrows(IllegalStateException.class,
                        () -> useCase(staleCatalog, stalePackages).publishProject(
                                "demo-project", "slow-sql", Map.of("content", "new"),
                                3, "hash-3", "SYSTEM_SKILL_EVOLVER")).getMessage());
    }

    private SkillEvolutionPublishUseCase useCase(ISkillCatalogRepository catalogRepository,
                                                  ISkillPackageRepository packageRepository) {
        return new SkillEvolutionPublishUseCase(
                catalogRepository,
                packageRepository,
                new ImmediateTransactionPort(),
                SkillPackageManifest.Limits.defaults());
    }

    private SkillCatalogEntry current(String status,
                                      String updateMode,
                                      boolean autoUpdateEnabled,
                                      int version,
                                      String hash) {
        SkillPackageManifest.Descriptor descriptor = baseDescriptor(version);
        return new SkillCatalogEntry(
                1L, "slow-sql", "demo-project", "Slow SQL", "PROJECT", "",
                "desc", "old content", version, status, "owner", null, null,
                "EVOLVED", updateMode, autoUpdateEnabled, true,
                LocalDateTime.parse("2026-07-18T00:00:00"), "", "", null,
                hash, version, hash, version, descriptor.packageHash(),
                descriptor.manifestJson(),
                com.alibaba.fastjson.JSON.toJSONString(descriptor.artifactHashes()));
    }

    private SkillPackageManifest.Descriptor baseDescriptor(int version) {
        return SkillPackageManifest.create(
                "PROJECT", "demo-project", "slow-sql", "Slow SQL", "desc", version, "old content",
                Map.of(
                        "category", "DATA",
                        "whenToUse", List.of("定位慢 SQL 和查询延迟"),
                        "whenNotToUse", List.of("生成演示文稿或开发前端页面"),
                        "keywords", List.of("slow sql", "explain"),
                        "artifacts", List.of(Map.of(
                                "path", "resources/query.yaml",
                                "role", "RESOURCE",
                                "content", "query: slow\n")),
                        "dependencies", List.of(Map.of(
                                "type", "TOOLSET",
                                "id", "database.read",
                                "version", "1"))),
                SkillPackageManifest.Limits.defaults());
    }

    private List<SkillArtifact> currentArtifacts() {
        return baseDescriptor(3).artifacts().values().stream()
                .map(artifact -> new SkillArtifact(
                        artifact.path(), artifact.role(), artifact.mediaType(), artifact.encoding(),
                        artifact.contentHash(), artifact.sizeBytes(), artifact.content()))
                .toList();
    }

    private static final class ImmediateTransactionPort implements SkillTransactionPort {
        @Override
        public <T> T required(Supplier<T> action) {
            return action.get();
        }
    }
}
