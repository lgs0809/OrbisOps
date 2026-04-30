package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillBindingMode;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillCurrentPointerUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleStatus;
import cn.lgs.orbisops.domain.skill.model.SkillLock;
import cn.lgs.orbisops.domain.skill.model.SkillLockType;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillRestoreAndForkUseCaseTest {

    @Test
    void quarantinedSkillCanRestoreKnownGoodVersionIntoShadowOnly() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillGovernanceAuthorizationPort authorization = mock(SkillGovernanceAuthorizationPort.class);
        SkillGovernanceAuditPort audit = mock(SkillGovernanceAuditPort.class);
        SkillCatalogEntry current = current(quarantined());
        SkillPackageVersion source = packageVersion(2);
        when(catalog.available()).thenReturn(true);
        when(packages.available()).thenReturn(true);
        when(catalog.find("PROJECT", "project-a", "skill-a", true)).thenReturn(Optional.of(current));
        when(packages.findVersion(source.key())).thenReturn(Optional.of(source));
        when(packages.findArtifacts(source.key())).thenReturn(artifacts(2));
        when(catalog.compareAndSetCurrent(any())).thenReturn(true);
        when(catalog.compareAndSetGovernance(any())).thenReturn(true);
        RestoreSkillUseCase useCase = new RestoreSkillUseCase(
                catalog, packages, new ImmediateTransactionPort(), SkillPackageManifest.Limits.defaults(),
                authorization, audit);
        SkillGovernanceCommand governance = command("skill-a", "hash-3", "approval-restore");

        SkillRollbackOutcome outcome = useCase.execute(new RestoreSkillCommand(governance, 2));

        assertEquals(4, outcome.publishedVersion().key().version());
        assertEquals("EMERGENCY_RESTORE", outcome.publishedVersion().publishMode());
        verify(authorization).require("admin", SkillGovernancePermission.SKILL_RESTORE,
                "PROJECT", "project-a", "skill-a");
        ArgumentCaptor<SkillCurrentPointerUpdate> pointer =
                ArgumentCaptor.forClass(SkillCurrentPointerUpdate.class);
        verify(catalog).compareAndSetCurrent(pointer.capture());
        assertEquals(3, pointer.getValue().baseVersion());
        assertEquals(4, pointer.getValue().nextVersion());
        assertEquals("EMERGENCY_RESTORE", pointer.getValue().origin());
        ArgumentCaptor<SkillGovernanceUpdate> state =
                ArgumentCaptor.forClass(SkillGovernanceUpdate.class);
        verify(catalog).compareAndSetGovernance(state.capture());
        assertEquals(4, state.getValue().expectedVersion());
        assertEquals(SkillMutationMode.MANUAL_ONLY, state.getValue().nextState().mutationMode());
        assertEquals(SkillExecutionMode.SHADOW_ONLY, state.getValue().nextState().executionMode());
        assertEquals(SkillLockType.NONE, state.getValue().nextState().lock().type());
        verify(packages).appendVersion(any(), any());
        verify(audit).record(any());
    }

    @Test
    void sealedSkillForkCreatesNewSkillWithoutChangingSourcePointer() {
        ISkillCatalogRepository catalog = mock(ISkillCatalogRepository.class);
        ISkillPackageRepository packages = mock(ISkillPackageRepository.class);
        SkillGovernanceAuthorizationPort authorization = mock(SkillGovernanceAuthorizationPort.class);
        SkillCatalogEntry sourceEntry = current(sealed());
        SkillPackageVersion sourceVersion = packageVersion(3);
        when(catalog.available()).thenReturn(true);
        when(packages.available()).thenReturn(true);
        when(catalog.find("PROJECT", "project-a", "skill-a", true))
                .thenReturn(Optional.of(sourceEntry));
        when(catalog.find("PROJECT", "project-a", "skill-fork", false))
                .thenReturn(Optional.empty());
        when(packages.findVersion(sourceVersion.key())).thenReturn(Optional.of(sourceVersion));
        when(packages.findArtifacts(sourceVersion.key())).thenReturn(artifacts(3));
        when(catalog.insertIfAbsent(any())).thenReturn(true);
        ForkSealedSkillUseCase useCase = new ForkSealedSkillUseCase(
                catalog, packages, new ImmediateTransactionPort(), SkillPackageManifest.Limits.defaults(),
                authorization, mock(SkillGovernanceAuditPort.class));

        SkillCatalogWriteOutcome outcome = useCase.execute(new ForkSealedSkillCommand(
                command("skill-a", "hash-3", "approval-fork"), "skill-fork", "Skill Fork"));

        assertTrue(outcome.created());
        assertEquals("skill-fork", outcome.publishedVersion().key().skillId());
        assertEquals(1, outcome.publishedVersion().key().version());
        assertEquals("SEALED_FORK", outcome.publishedVersion().publishMode());
        ArgumentCaptor<SkillCatalogEntry> target = ArgumentCaptor.forClass(SkillCatalogEntry.class);
        verify(catalog).insertIfAbsent(target.capture());
        assertEquals(SkillMutationMode.MANUAL_ONLY, target.getValue().governanceState().mutationMode());
        assertEquals(SkillExecutionMode.ENABLED, target.getValue().governanceState().executionMode());
        verify(catalog, never()).compareAndSetCurrent(any());
        verify(catalog, never()).compareAndSetGovernance(any());
        verify(authorization).require("admin", SkillGovernancePermission.SKILL_FORK,
                "PROJECT", "project-a", "skill-a");
    }

    private SkillGovernanceCommand command(String skillId, String expectedHash, String approvalId) {
        return new SkillGovernanceCommand(
                "PROJECT", "project-a", skillId, 3, expectedHash,
                "approved governance operation", approvalId, "admin");
    }

    private SkillCatalogEntry current(SkillGovernanceState state) {
        return new SkillCatalogEntry(
                1L, "skill-a", "project-a", "Skill A", "PROJECT", "", "desc", "current content",
                3, state.legacyStatusProjection(), "admin", null, null, "MANUAL",
                state.legacyUpdateModeProjection(), true, true,
                LocalDateTime.parse("2026-07-18T00:00:00"),
                state.lock().reason(), state.lock().actor(), state.lock().lockedAt(),
                "hash-3", 3, "hash-3", 3, "package-hash-3",
                "{\"kind\":\"SkillPackage\"}", "{\"SKILL.md\":\"hash-3\"}", state);
    }

    private SkillGovernanceState quarantined() {
        return new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.MANUAL_ONLY,
                SkillExecutionMode.QUARANTINED, SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.INCIDENT_QUARANTINE, "incident", "admin", "approval-q",
                        LocalDateTime.parse("2026-08-01T00:00:00")), false);
    }

    private SkillGovernanceState sealed() {
        return new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.SEALED,
                SkillExecutionMode.ENABLED, SkillBindingMode.PINNED,
                new SkillLock(SkillLockType.COMPLIANCE_SEAL, "approved baseline", "admin", "approval-s",
                        LocalDateTime.parse("2026-08-01T00:00:00")), false);
    }

    private SkillPackageVersion packageVersion(int version) {
        SkillPackageManifest.Descriptor descriptor = descriptor(version);
        return new SkillPackageVersion(
                version, new SkillPackageKey("PROJECT", "project-a", "skill-a", version),
                "hash-" + version, Math.max(0, version - 1),
                version > 1 ? "hash-" + (version - 1) : "", "", "", "",
                "MANUAL", "known good content " + version, "MANUAL", "trace-" + version,
                "version " + version, descriptor.packageHash(), descriptor.manifestJson(),
                com.alibaba.fastjson.JSON.toJSONString(descriptor.artifactHashes()), descriptor.entrypoint(),
                descriptor.artifactCount(), descriptor.packageSize(), Instant.parse("2026-07-17T00:00:00Z"));
    }

    private SkillPackageManifest.Descriptor descriptor(int version) {
        return SkillPackageManifest.packageOf(
                "PROJECT", "project-a", "skill-a", "Skill A", "desc", version,
                "known good content " + version,
                List.of(Map.of("path", "evals/cases.json", "role", "EVAL", "content", "[]")),
                List.of(), List.of("evals/cases.json"), SkillPackageManifest.Limits.defaults());
    }

    private List<SkillArtifact> artifacts(int version) {
        return descriptor(version).artifacts().values().stream()
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
