package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogMutation;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillCatalogMutationUseCaseTest {

    @Test
    void createInsertsCurrentPointerAndAppendsInitialImmutablePackage() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.insertIfAbsent(any())).thenReturn(true);
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);

        SkillCatalogWriteOutcome outcome = useCase.createProject(
                "project-1",
                withRouting(Map.of(
                        "skillId", "Slow SQL",
                        "name", "Slow SQL",
                        "description", "diagnose slow queries",
                        "content", "# Steps\n- EXPLAIN",
                        "status", "ACTIVE",
                        "updateMode", "AUTO",
                        "autoUpdateEnabled", true,
                        "autoMergeEnabled", true)),
                "alice");

        assertTrue(outcome.created());
        assertEquals("slow-sql", outcome.publishedVersion().key().skillId());
        assertEquals(1, outcome.publishedVersion().key().version());
        assertEquals("MANUAL_CREATE", outcome.publishedVersion().publishMode());
        assertEquals("alice", outcome.publishedVersion().sourceTraceId());
        ArgumentCaptor<SkillCatalogEntry> entry = ArgumentCaptor.forClass(SkillCatalogEntry.class);
        verify(catalogRepository).insertIfAbsent(entry.capture());
        assertEquals("alice", entry.getValue().createBy());
        assertEquals(entry.getValue().skillHash(), entry.getValue().currentSkillHash());
        assertFalse(entry.getValue().currentPackageHash().isBlank());
        verify(packageRepository).appendVersion(any(), any());
    }

    @Test
    void duplicateCreateNeverAppendsVersion() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.insertIfAbsent(any())).thenReturn(false);
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.createGlobal(withRouting(Map.of(
                        "skillId", "slow-sql",
                        "content", "# Steps")), "alice"));

        assertEquals("Skill 已存在：slow-sql", error.getMessage());
        verify(packageRepository, never()).appendVersion(any(), any());
    }

    @Test
    void missingProjectFailsBeforeTouchingSkillStores() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillProjectValidationPort projectValidation = mock(SkillProjectValidationPort.class);
        doThrow(new IllegalArgumentException("项目不存在：missing"))
                .when(projectValidation).requireExisting("missing");
        SkillCatalogMutationUseCase useCase = new SkillCatalogMutationUseCase(
                catalogRepository, packageRepository, new ImmediateTransactionPort(),
                projectValidation, SkillPackageManifest.Limits.defaults());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.createProject(
                        "missing", Map.of("skillId", "slow-sql", "content", "# Steps"), "alice"));

        assertEquals("项目不存在：missing", error.getMessage());
        verifyNoInteractions(catalogRepository, packageRepository);
    }

    @Test
    void existingUpdateUsesSnapshotCasAndPreservesCurrentPackageFiles() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry current = current("ACTIVE", "MANUAL_ONLY", false, 3, "hash-3");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "project-1", "slow-sql", true))
                .thenReturn(Optional.of(current));
        when(packageRepository.findArtifacts(current.key())).thenReturn(currentArtifacts());
        when(catalogRepository.compareAndSetMutation(any())).thenReturn(true);
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);

        SkillCatalogWriteOutcome outcome = useCase.updateProject(
                "project-1", "slow-sql", snapshot(current),
                Map.of(
                        "description", "updated",
                        "updateMode", "AUTO",
                        "autoUpdateEnabled", true,
                        "changeSummary", "enable governed auto update"),
                "alice");

        assertFalse(outcome.created());
        assertEquals(4, outcome.publishedVersion().key().version());
        assertEquals(3, outcome.publishedVersion().baseVersion());
        assertEquals("hash-3", outcome.publishedVersion().baseSkillHash());
        assertEquals("MANUAL_UPDATE", outcome.publishedVersion().publishMode());
        ArgumentCaptor<SkillCatalogMutation> mutation = ArgumentCaptor.forClass(SkillCatalogMutation.class);
        verify(catalogRepository).compareAndSetMutation(mutation.capture());
        assertEquals(3, mutation.getValue().baseVersion());
        assertEquals("hash-3", mutation.getValue().baseSkillHash());
        assertEquals(4, mutation.getValue().nextVersion());
        assertEquals("AUTO", mutation.getValue().updateMode());
        assertTrue(mutation.getValue().autoUpdateEnabled());
        assertNotEquals("hash-3", mutation.getValue().nextSkillHash());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SkillArtifact>> artifacts = ArgumentCaptor.forClass(List.class);
        verify(packageRepository).appendVersion(any(), artifacts.capture());
        assertTrue(artifacts.getValue().stream()
                .anyMatch(item -> "resources/query.yaml".equals(item.path())));
        assertTrue(outcome.publishedVersion().manifestJson().contains("database.read"));
    }

    @Test
    void transactionCurrentStateWinsOverStaleNonIdentitySnapshotFields() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry current = current("ACTIVE", "AUTO", true, 3, "hash-3");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "project-1", "slow-sql", true))
                .thenReturn(Optional.of(current));
        when(packageRepository.findArtifacts(current.key())).thenReturn(currentArtifacts());
        when(catalogRepository.compareAndSetMutation(any())).thenReturn(true);
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);
        Map<String, Object> staleSnapshot = snapshot(current);
        staleSnapshot.put("description", "stale description");
        staleSnapshot.put("content", "stale content");

        useCase.updateProject(
                "project-1", "slow-sql", staleSnapshot,
                Map.of("updateMode", "MANUAL_ONLY"), "alice");

        ArgumentCaptor<SkillCatalogMutation> mutation = ArgumentCaptor.forClass(SkillCatalogMutation.class);
        verify(catalogRepository).compareAndSetMutation(mutation.capture());
        assertEquals("desc", mutation.getValue().description());
        assertEquals("old content", mutation.getValue().content());
        assertEquals("MANUAL_ONLY", mutation.getValue().updateMode());
    }

    @Test
    void explicitStaleBaseFailsBeforeReadingPackageArtifacts() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry current = current("ACTIVE", "AUTO", true, 4, "hash-4");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "project-1", "slow-sql", true))
                .thenReturn(Optional.of(current));
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);
        Map<String, Object> staleSnapshot = snapshot(current("ACTIVE", "AUTO", true, 3, "hash-3"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.updateProject(
                        "project-1", "slow-sql", staleSnapshot,
                        Map.of("description", "stale", "baseVersion", 3, "baseSkillHash", "hash-3"),
                        "alice"));

        assertEquals("SKILL_CURRENT_POINTER_CONFLICT：slow-sql", error.getMessage());
        verify(packageRepository, never()).findArtifacts(any());
        verify(catalogRepository, never()).compareAndSetMutation(any());
    }

    @Test
    void mutationCasConflictDoesNotAppendVersion() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry current = current("ACTIVE", "AUTO", true, 3, "hash-3");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "project-1", "slow-sql", true))
                .thenReturn(Optional.of(current));
        when(packageRepository.findArtifacts(current.key())).thenReturn(currentArtifacts());
        when(catalogRepository.compareAndSetMutation(any())).thenReturn(false);
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.updateProject(
                        "project-1", "slow-sql", snapshot(current),
                        Map.of("description", "racing update"), "alice"));

        assertEquals("SKILL_CURRENT_POINTER_CONFLICT：slow-sql", error.getMessage());
        verify(packageRepository, never()).appendVersion(any(), any());
    }

    @Test
    void fileBackedSkillMaterializationCreatesNextVersionWithBaseIdentity() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("GLOBAL", "", "slow-sql", true))
                .thenReturn(Optional.empty());
        when(catalogRepository.insertIfAbsent(any())).thenReturn(true);
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);
        Map<String, Object> fileSnapshot = new LinkedHashMap<>();
        fileSnapshot.put("skillId", "slow-sql");
        fileSnapshot.put("name", "Slow SQL");
        fileSnapshot.put("description", "file skill");
        fileSnapshot.put("content", "# File Steps");
        fileSnapshot.put("currentVersion", 1);
        fileSnapshot.put("currentSkillHash", "file-hash-1");
        fileSnapshot.put("status", "ENABLED");
        fileSnapshot.put("origin", "IMPORTED");
        fileSnapshot.put("updateMode", "MANUAL_ONLY");
        fileSnapshot.put("autoUpdateEnabled", false);
        fileSnapshot.put("autoMergeEnabled", false);
        fileSnapshot.putAll(routing());

        SkillCatalogWriteOutcome outcome = useCase.updateGlobal(
                "slow-sql", fileSnapshot, Map.of("description", "materialized"), "alice");

        assertTrue(outcome.created());
        assertEquals(2, outcome.publishedVersion().key().version());
        assertEquals(1, outcome.publishedVersion().baseVersion());
        assertEquals("file-hash-1", outcome.publishedVersion().baseSkillHash());
        assertEquals("MANUAL_MATERIALIZE", outcome.publishedVersion().publishMode());
        ArgumentCaptor<SkillCatalogEntry> entry = ArgumentCaptor.forClass(SkillCatalogEntry.class);
        verify(catalogRepository).insertIfAbsent(entry.capture());
        assertEquals(2, entry.getValue().currentVersion());
        assertEquals("alice", entry.getValue().createBy());
    }

    @Test
    void genericMutationRejectsCriticalFreezeGovernance() {
        ISkillCatalogRepository catalogRepository = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packageRepository = mock(ISkillPackageRepository.class);
        SkillCatalogEntry current = current("ACTIVE", "AUTO", true, 3, "hash-3");
        when(catalogRepository.available()).thenReturn(true);
        when(packageRepository.available()).thenReturn(true);
        when(catalogRepository.find("PROJECT", "project-1", "slow-sql", true))
                .thenReturn(Optional.of(current));
        SkillCatalogMutationUseCase useCase = useCase(catalogRepository, packageRepository);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.updateProject(
                        "project-1", "slow-sql", snapshot(current),
                        Map.of("status", "FROZEN", "frozenReason", "manual review"), "alice"));

        assertEquals("SKILL_GOVERNANCE_DEDICATED_USE_CASE_REQUIRED:status", error.getMessage());
        verify(catalogRepository, never()).compareAndSetMutation(any());
        verify(packageRepository, never()).appendVersion(any(), any());
    }

    private SkillCatalogMutationUseCase useCase(ISkillCatalogRepository catalogRepository,
                                                 ISkillPackageRepository packageRepository) {
        return new SkillCatalogMutationUseCase(
                catalogRepository, packageRepository,
                new ImmediateTransactionPort(), projectId -> { },
                SkillPackageManifest.Limits.defaults());
    }

    private SkillCatalogEntry current(String status,
                                      String updateMode,
                                      boolean autoUpdateEnabled,
                                      int version,
                                      String hash) {
        SkillPackageManifest.Descriptor descriptor = descriptor(version);
        return new SkillCatalogEntry(
                1L, "slow-sql", "project-1", "Slow SQL", "PROJECT", "",
                "desc", "old content", version, status, "owner", null, null,
                "MANUAL", updateMode, autoUpdateEnabled, true,
                LocalDateTime.parse("2026-07-18T00:00:00"), "", "", null,
                hash, version, hash, version, descriptor.packageHash(),
                descriptor.manifestJson(),
                com.alibaba.fastjson.JSON.toJSONString(descriptor.artifactHashes()));
    }

    private Map<String, Object> snapshot(SkillCatalogEntry current) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillId", current.skillId());
        result.put("projectId", current.projectId());
        result.put("name", current.name());
        result.put("description", current.description());
        result.put("content", current.content());
        result.put("currentVersion", current.currentVersion());
        result.put("currentSkillHash", current.currentSkillHash());
        result.put("status", current.status());
        result.put("origin", current.origin());
        result.put("updateMode", current.updateMode());
        result.put("autoUpdateEnabled", current.autoUpdateEnabled());
        result.put("autoMergeEnabled", current.autoMergeEnabled());
        result.put("createBy", current.createBy());
        result.put("packageManifestJson", current.packageManifestJson());
        return result;
    }

    private SkillPackageManifest.Descriptor descriptor(int version) {
        return SkillPackageManifest.create(
                "PROJECT", "project-1", "slow-sql", "Slow SQL", "desc", version, "old content",
                withRouting(Map.of(
                        "artifacts", List.of(Map.of(
                                "path", "resources/query.yaml",
                                "role", "RESOURCE",
                                "content", "query: slow\n")),
                        "dependencies", List.of(Map.of(
                                "type", "TOOLSET",
                                "id", "database.read",
                                "version", "1")))),
                SkillPackageManifest.Limits.defaults());
    }

    private Map<String, Object> routing() {
        return Map.of(
                "category", "DATA",
                "whenToUse", List.of("定位慢 SQL 和查询延迟"),
                "whenNotToUse", List.of("生成演示文稿或开发前端页面"),
                "keywords", List.of("slow sql", "explain"));
    }

    private Map<String, Object> withRouting(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.putAll(routing());
        return result;
    }

    private List<SkillArtifact> currentArtifacts() {
        return descriptor(3).artifacts().values().stream()
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
