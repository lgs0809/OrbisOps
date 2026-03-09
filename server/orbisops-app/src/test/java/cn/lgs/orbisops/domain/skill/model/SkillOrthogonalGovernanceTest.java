package cn.lgs.orbisops.domain.skill.model;

import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillOrthogonalGovernanceTest {

    @Test
    void lockedEnabledRemainsRunnableButBlocksMutationAndAutoPublish() {
        SkillGovernanceState state = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE,
                SkillMutationMode.LOCKED,
                SkillExecutionMode.ENABLED,
                SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.MANUAL_LOCK, "stability", "admin", "approval-1",
                        LocalDateTime.parse("2026-08-02T00:00:00")),
                false);

        assertTrue(state.activeAtUse());
        assertFalse(state.ordinaryMutationAllowed());
        assertFalse(state.autoPublishAllowed());
        assertThrows(IllegalStateException.class,
                () -> new SkillGovernancePolicy().requireOrdinaryMutation(state));
    }

    @Test
    void quarantinedSkillIsExcludedFromFormalRuntimeButAllowsManualRepair() {
        SkillGovernanceState state = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE,
                SkillMutationMode.MANUAL_ONLY,
                SkillExecutionMode.QUARANTINED,
                SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.INCIDENT_QUARANTINE, "incident", "admin", "",
                        LocalDateTime.parse("2026-08-02T00:00:00")),
                false);

        assertFalse(state.activeAtUse());
        assertTrue(state.ordinaryMutationAllowed());
    }

    @Test
    void sealedEnabledRemainsRunnableButCannotBeMutatedOrEmergencyRestored() {
        SkillGovernanceState state = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE,
                SkillMutationMode.SEALED,
                SkillExecutionMode.ENABLED,
                SkillBindingMode.PINNED,
                new SkillLock(SkillLockType.COMPLIANCE_SEAL, "approved baseline", "admin", "approval-2",
                        LocalDateTime.parse("2026-08-02T00:00:00")),
                false);
        SkillCatalogEntry entry = entry(state);

        assertTrue(state.activeAtUse());
        assertFalse(state.ordinaryMutationAllowed());
        assertThrows(IllegalStateException.class,
                () -> new SkillGovernancePolicy().requireEmergencyRestore(entry, 1));
    }

    @Test
    void legacyFrozenAlwaysMigratesFailClosedAndRequiresClassification() {
        SkillGovernanceState state = SkillGovernanceState.fromLegacy(
                "FROZEN", "FROZEN", "", "", null);

        assertEquals(SkillMutationMode.LOCKED, state.mutationMode());
        assertEquals(SkillExecutionMode.QUARANTINED, state.executionMode());
        assertEquals(SkillLockType.LEGACY_UNCLASSIFIED, state.lock().type());
        assertTrue(state.legacyFrozenClassificationRequired());
        assertFalse(state.activeAtUse());
    }

    @Test
    void runtimeCandidateSnapshotIsUnaffectedByLaterCatalogGovernanceChange() {
        SkillGovernanceState lockedEnabled = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.LOCKED, SkillExecutionMode.ENABLED,
                SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.MANUAL_LOCK, "freeze edits", "admin", "",
                        LocalDateTime.parse("2026-08-02T00:00:00")), false);
        SkillRuntimeCandidate snapshot = new SkillRuntimeCandidate(
                "skill-a", "project-a", "PROJECT", "Skill A", "desc", 3, "hash-3",
                "package-3", "manifest-3", java.util.Map.of(), "SKILL.md",
                "ENABLED", "LOCKED", 100, SkillRoutingProfile.empty("desc"), lockedEnabled);
        SkillGovernanceState laterCatalogState = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.LOCKED, SkillExecutionMode.QUARANTINED,
                SkillBindingMode.FLOATING, lockedEnabled.lock(), false);

        assertTrue(snapshot.activeAtUse());
        assertFalse(laterCatalogState.activeAtUse());
        assertTrue(snapshot.activeAtUse());
    }

    private SkillCatalogEntry entry(SkillGovernanceState state) {
        return new SkillCatalogEntry(
                1L, "skill-a", "project-a", "Skill A", "PROJECT", "", "desc", "content",
                3, state.legacyStatusProjection(), "admin", null, null, "MANUAL",
                state.legacyUpdateModeProjection(), true, true, null,
                state.lock().reason(), state.lock().actor(), state.lock().lockedAt(),
                "hash-3", 3, "hash-3", 3, "package-3", "{}", "{}", state);
    }
}
