package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCurrentPointerUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillRollbackUseCaseTest {

    @Test
    void newMethodWithoutBaselineIsActuallyQuarantinedAndPreservesVersion() {
        var catalog = mock(ISkillCatalogRepository.class);
        var packages = mock(ISkillPackageRepository.class);
        when(catalog.available()).thenReturn(true); when(packages.available()).thenReturn(true);
        when(catalog.find("PROJECT", "project-1", "slow-sql", true)).thenReturn(Optional.of(projectCurrent("ENABLED", "AUTO")));
        when(catalog.compareAndSetGovernance(any())).thenReturn(true);
        var result = useCase(catalog, packages).recoverRelease(release(0, "", 3));
        assertEquals(false, result.restored()); assertEquals(3, result.version());
        var update = ArgumentCaptor.forClass(cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate.class);
        verify(catalog).compareAndSetGovernance(update.capture());
        assertEquals(cn.lgs.orbisops.domain.skill.model.SkillExecutionMode.QUARANTINED, update.getValue().nextState().executionMode());
        verify(catalog, never()).compareAndSetCurrent(any());
        verify(packages, never()).appendVersion(any(), any());
    }

    @Test
    void unknownBaselineSafetyIsNotTreatedAsPermissionToRestore() {
        var catalog = mock(ISkillCatalogRepository.class);
        var packages = mock(ISkillPackageRepository.class);
        when(catalog.available()).thenReturn(true); when(packages.available()).thenReturn(true);
        when(catalog.find("PROJECT", "project-1", "slow-sql", true)).thenReturn(Optional.of(projectCurrent("ENABLED", "AUTO")));
        var key = new SkillPackageKey("PROJECT", "project-1", "slow-sql", 2);
        when(packages.findVersion(key)).thenReturn(Optional.of(copyWithKey(sourceVersion(), key)));
        when(catalog.compareAndSetGovernance(any())).thenReturn(true);
        assertEquals(false, useCase(catalog, packages).recoverRelease(release(2, "hash-2", 3)).restored());
        verify(catalog, never()).compareAndSetCurrent(any());
    }

    @Test
    void verifiedCompatibleBaselineRestoresContentAsANewImmutableVersion() {
        var catalog = mock(ISkillCatalogRepository.class);
        var packages = mock(ISkillPackageRepository.class);
        when(catalog.available()).thenReturn(true); when(packages.available()).thenReturn(true);
        when(catalog.find("PROJECT", "project-1", "slow-sql", true)).thenReturn(Optional.of(projectCurrent("ENABLED", "AUTO")));
        var key = new SkillPackageKey("PROJECT", "project-1", "slow-sql", 2);
        when(packages.findVersion(key)).thenReturn(Optional.of(copyWithKey(sourceVersion(), key)));
        when(packages.findArtifacts(key)).thenReturn(sourceArtifacts());
        when(catalog.compareAndSetCurrent(any())).thenReturn(true);
        var useCase = new SkillRollbackUseCase(catalog, packages, new ImmediateTransactionPort(), SkillPackageManifest.Limits.defaults(),
                (release, baseline) -> true);
        var restored = useCase.recoverRelease(release(2, "hash-2", 3));
        assertEquals(true, restored.restored()); assertEquals(4, restored.version());
        verify(packages).appendVersion(any(), any());
        verify(catalog, never()).compareAndSetGovernance(any());
    }

    @Test
    void disabledMethodIsNotReportedAsRestoredEvenWithACompatibleHistoricalVersion() {
        var catalog = mock(ISkillCatalogRepository.class);
        var packages = mock(ISkillPackageRepository.class);
        when(catalog.available()).thenReturn(true); when(packages.available()).thenReturn(true);
        when(catalog.find("PROJECT", "project-1", "slow-sql", true)).thenReturn(Optional.of(projectCurrent("DISABLED", "AUTO")));
        var key = new SkillPackageKey("PROJECT", "project-1", "slow-sql", 2);
        when(packages.findVersion(key)).thenReturn(Optional.of(copyWithKey(sourceVersion(), key)));
        when(catalog.compareAndSetGovernance(any())).thenReturn(true);
        var useCase = new SkillRollbackUseCase(catalog, packages, new ImmediateTransactionPort(), SkillPackageManifest.Limits.defaults(),
                (release, baseline) -> true);
        assertEquals(false, useCase.recoverRelease(release(2, "hash-2", 3)).restored());
        verify(catalog, never()).compareAndSetCurrent(any());
        verify(packages, never()).appendVersion(any(), any());
    }

    @Test
    void lateReleaseRecoveryCannotOverwriteOrDisableNewerUserVersion() {
        var catalog = mock(ISkillCatalogRepository.class);
        var packages = mock(ISkillPackageRepository.class);
        when(catalog.available()).thenReturn(true); when(packages.available()).thenReturn(true);
        when(catalog.find("PROJECT", "project-1", "slow-sql", true)).thenReturn(Optional.of(projectCurrent("ENABLED", "AUTO")));
        assertThrows(IllegalStateException.class, () -> useCase(catalog, packages).recoverRelease(release(0, "", 2)));
        verify(catalog, never()).compareAndSetCurrent(any()); verify(catalog, never()).compareAndSetGovernance(any());
    }

    private cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot release(int baseline, String baseHash, int version) {
        return new cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot("release", "candidate", "project-1", "agent", "slow-sql",
                cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus.ROLLING_BACK, 10, baseline, baseHash, "", version, "hash-" + version, Map.of());
    }

    @Test
    void rollbackAdvancesCurrentPointerAndAppendsImmutableVersion() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillPackageVersion source = sourceVersion();
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("GLOBAL", "", "slow-sql", true))
                .thenReturn(Optional.of(current("ENABLED", "MANUAL_ONLY")));
        when(packageRepository.findVersion(source.key())).thenReturn(Optional.of(source));
        when(packageRepository.findArtifacts(source.key())).thenReturn(sourceArtifacts());
        when(catalogRepository.compareAndSetCurrent(any())).thenReturn(true);
        SkillRollbackUseCase useCase = useCase(catalogRepository, packageRepository);

        SkillRollbackOutcome outcome = useCase.rollbackGlobal("Slow SQL", 2, "alice");

        assertEquals(3, outcome.previous().currentVersion());
        assertEquals(4, outcome.publishedVersion().key().version());
        ArgumentCaptor<SkillCurrentPointerUpdate> pointer =
                ArgumentCaptor.forClass(SkillCurrentPointerUpdate.class);
        verify(catalogRepository).compareAndSetCurrent(pointer.capture());
        assertEquals("GLOBAL", pointer.getValue().scope());
        assertEquals(3, pointer.getValue().baseVersion());
        assertEquals("hash-3", pointer.getValue().baseSkillHash());
        assertEquals(4, pointer.getValue().nextVersion());
        assertEquals("MANUAL", pointer.getValue().origin());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SkillArtifact>> artifacts = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<SkillPackageVersion> version = ArgumentCaptor.forClass(SkillPackageVersion.class);
        verify(packageRepository).appendVersion(version.capture(), artifacts.capture());
        assertEquals("MANUAL_ROLLBACK", version.getValue().publishMode());
        assertEquals("alice", version.getValue().sourceTraceId());
        assertEquals("ROLLBACK", version.getValue().sourceType());
        assertEquals(3, artifacts.getValue().size());
        assertTrue(artifacts.getValue().stream()
                .anyMatch(item -> "resources/query.yaml".equals(item.path())));
        assertTrue(artifacts.getValue().stream()
                .anyMatch(item -> "evals/cases.json".equals(item.path())));
    }

    @Test
    void currentPointerConflictDoesNotAppendVersion() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillPackageVersion source = sourceVersion();
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("GLOBAL", "", "slow-sql", true))
                .thenReturn(Optional.of(current("ENABLED", "MANUAL_ONLY")));
        when(packageRepository.findVersion(source.key())).thenReturn(Optional.of(source));
        when(packageRepository.findArtifacts(source.key())).thenReturn(sourceArtifacts());
        when(catalogRepository.compareAndSetCurrent(any())).thenReturn(false);
        SkillRollbackUseCase useCase = useCase(catalogRepository, packageRepository);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.rollbackGlobal("slow-sql", 2, "alice"));

        assertEquals("SKILL_CURRENT_POINTER_CONFLICT：slow-sql", error.getMessage());
        verify(packageRepository, never()).appendVersion(any(), any());
    }

    @Test
    void systemRollbackUsesDistinctPublishMode() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillPackageVersion source = sourceVersion();
        SkillPackageKey projectSourceKey = new SkillPackageKey("PROJECT", "project-1", "slow-sql", 2);
        SkillPackageVersion projectSource = copyWithKey(source, projectSourceKey);
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "project-1", "slow-sql", true))
                .thenReturn(Optional.of(projectCurrent("ENABLED", "AUTO")));
        when(packageRepository.findVersion(projectSourceKey)).thenReturn(Optional.of(projectSource));
        when(packageRepository.findArtifacts(projectSourceKey)).thenReturn(sourceArtifacts());
        when(catalogRepository.compareAndSetCurrent(any())).thenReturn(true);
        SkillRollbackUseCase useCase = useCase(catalogRepository, packageRepository);

        SkillRollbackOutcome outcome = useCase.rollbackProject(
                "project-1", "slow-sql", 2, "SYSTEM_SKILL_RELEASE_GUARD");

        assertEquals("SYSTEM_ROLLBACK", outcome.publishedVersion().publishMode());
        assertEquals("SYSTEM_SKILL_RELEASE_GUARD", outcome.publishedVersion().sourceTraceId());
    }

    @Test
    void frozenSkillFailsBeforeReadingHistoricalVersion() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("GLOBAL", "", "slow-sql", true))
                .thenReturn(Optional.of(current("FROZEN", "MANUAL_ONLY")));
        SkillRollbackUseCase useCase = useCase(catalogRepository, packageRepository);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.rollbackGlobal("slow-sql", 2, "alice"));

        assertEquals("SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED", error.getMessage());
        verify(packageRepository, never()).findVersion(any());
    }

    private SkillRollbackUseCase useCase(ISkillCatalogRepository catalogRepository,
                                          ISkillPackageRepository packageRepository) {
        return new SkillRollbackUseCase(
                catalogRepository,
                packageRepository,
                new ImmediateTransactionPort(),
                SkillPackageManifest.Limits.defaults());
    }

    private SkillCatalogEntry current(String status, String updateMode) {
        return current("GLOBAL", "", status, updateMode);
    }

    private SkillCatalogEntry projectCurrent(String status, String updateMode) {
        return current("PROJECT", "project-1", status, updateMode);
    }

    private SkillCatalogEntry current(String scope,
                                      String projectId,
                                      String status,
                                      String updateMode) {
        return new SkillCatalogEntry(1L, "slow-sql", projectId, "Slow SQL", scope, "",
                "desc", "current content", 3, status, "owner", null, null,
                "MANUAL", updateMode, true, true,
                LocalDateTime.parse("2026-07-18T00:00:00"), "", "", null,
                "hash-3", 3, "hash-3", 3, "package-hash-3",
                "{\"kind\":\"SkillPackage\"}", "{\"SKILL.md\":\"hash-3\"}");
    }

    private SkillPackageVersion sourceVersion() {
        SkillPackageManifest.Descriptor descriptor = sourceDescriptor();
        return new SkillPackageVersion(2L, new SkillPackageKey("GLOBAL", "", "slow-sql", 2),
                "hash-2", 1, "hash-1", "", "", "", "MANUAL",
                "rollback content", "MANUAL", "trace-2", "version 2",
                descriptor.packageHash(), descriptor.manifestJson(),
                com.alibaba.fastjson.JSON.toJSONString(descriptor.artifactHashes()),
                descriptor.entrypoint(), descriptor.artifactCount(), descriptor.packageSize(),
                Instant.parse("2026-07-17T00:00:00Z"));
    }

    private SkillPackageVersion copyWithKey(SkillPackageVersion source, SkillPackageKey key) {
        return new SkillPackageVersion(source.id(), key, source.skillHash(), source.baseVersion(),
                source.baseSkillHash(), source.sourceRunId(), source.sourceSessionId(),
                source.evolutionJobId(), source.publishMode(), source.content(), source.sourceType(),
                source.sourceTraceId(), source.changeSummary(), source.packageHash(), source.manifestJson(),
                source.artifactHashesJson(), source.entrypoint(), source.artifactCount(),
                source.packageSize(), source.createdAt());
    }

    private SkillPackageManifest.Descriptor sourceDescriptor() {
        return SkillPackageManifest.packageOf(
                "GLOBAL", "", "slow-sql", "Slow SQL", "desc", 2, "rollback content",
                List.of(
                        Map.of("path", "resources/query.yaml", "role", "RESOURCE", "content", "query: slow\n"),
                        Map.of("path", "evals/cases.json", "role", "EVAL", "content", "[]")),
                List.of(Map.of("type", "TOOLSET", "id", "database.read", "version", "1")),
                List.of("evals/cases.json"), SkillPackageManifest.Limits.defaults());
    }

    private List<SkillArtifact> sourceArtifacts() {
        return sourceDescriptor().artifacts().values().stream()
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
