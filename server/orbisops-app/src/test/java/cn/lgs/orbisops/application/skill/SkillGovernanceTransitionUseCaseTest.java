package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.model.SkillBindingMode;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleStatus;
import cn.lgs.orbisops.domain.skill.model.SkillLock;
import cn.lgs.orbisops.domain.skill.model.SkillLockType;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGovernanceTransitionUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void unlockRequiresIndependentPermissionAndFallsBackToManualOnly() {
        ISkillCatalogRepository repository = mock(ISkillCatalogRepository.class);
        SkillGovernanceAuthorizationPort authorization = mock(SkillGovernanceAuthorizationPort.class);
        SkillGovernanceAuditPort audit = mock(SkillGovernanceAuditPort.class);
        SkillGovernanceState locked = lockedEnabled();
        SkillGovernanceState unlocked = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.MANUAL_ONLY, SkillExecutionMode.ENABLED,
                SkillBindingMode.FLOATING, SkillLock.none(), false);
        when(repository.available()).thenReturn(true);
        when(repository.find("PROJECT", "project-a", "skill-a", true))
                .thenReturn(Optional.of(entry(locked)), Optional.of(entry(unlocked)));
        when(repository.compareAndSetGovernance(any())).thenReturn(true);
        SkillGovernanceTransitionUseCase useCase = new SkillGovernanceTransitionUseCase(
                repository, directTransaction(), authorization, audit, CLOCK);

        useCase.unlock(command("approval-1"));

        verify(authorization).require("admin", SkillGovernancePermission.SKILL_UNLOCK,
                "PROJECT", "project-a", "skill-a");
        ArgumentCaptor<SkillGovernanceUpdate> update = ArgumentCaptor.forClass(SkillGovernanceUpdate.class);
        verify(repository).compareAndSetGovernance(update.capture());
        assertEquals(SkillMutationMode.MANUAL_ONLY, update.getValue().nextState().mutationMode());
        assertEquals(SkillExecutionMode.ENABLED, update.getValue().nextState().executionMode());
        assertEquals(SkillLockType.NONE, update.getValue().nextState().lock().type());
    }

    @Test
    void unlockFailsClosedOnGovernanceCasConflict() {
        ISkillCatalogRepository repository = mock(ISkillCatalogRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.find("PROJECT", "project-a", "skill-a", true))
                .thenReturn(Optional.of(entry(lockedEnabled())));
        when(repository.compareAndSetGovernance(any())).thenReturn(false);
        SkillGovernanceAuditPort audit = mock(SkillGovernanceAuditPort.class);
        SkillGovernanceTransitionUseCase useCase = new SkillGovernanceTransitionUseCase(
                repository, directTransaction(), mock(SkillGovernanceAuthorizationPort.class), audit, CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.unlock(command("approval-1")));

        assertEquals("SKILL_GOVERNANCE_CAS_CONFLICT:skill-a", error.getMessage());
        verify(audit, never()).record(any());
    }

    @Test
    void sealedSkillCannotBeUnlocked() {
        ISkillCatalogRepository repository = mock(ISkillCatalogRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.find("PROJECT", "project-a", "skill-a", true))
                .thenReturn(Optional.of(entry(sealedEnabled())));
        SkillGovernanceTransitionUseCase useCase = new SkillGovernanceTransitionUseCase(
                repository, directTransaction(), mock(SkillGovernanceAuthorizationPort.class),
                mock(SkillGovernanceAuditPort.class), CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.unlock(command("approval-1")));

        assertEquals("SKILL_UNLOCK_FORBIDDEN_WHILE_SEALED", error.getMessage());
        verify(repository, never()).compareAndSetGovernance(any());
    }

    @Test
    void legacyFrozenCannotBeUnlockedWithoutExplicitClassification() {
        ISkillCatalogRepository repository = mock(ISkillCatalogRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.find("PROJECT", "project-a", "skill-a", true))
                .thenReturn(Optional.of(entry(SkillGovernanceState.fromLegacy(
                        "FROZEN", "FROZEN", "old freeze", "admin", LocalDateTime.now(CLOCK)))));
        SkillGovernanceTransitionUseCase useCase = new SkillGovernanceTransitionUseCase(
                repository, directTransaction(), mock(SkillGovernanceAuthorizationPort.class),
                mock(SkillGovernanceAuditPort.class), CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.unlock(command("approval-1")));

        assertEquals("SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED", error.getMessage());
    }

    @Test
    void legacyFrozenCanBeExplicitlyClassifiedAsStabilityLock() {
        ISkillCatalogRepository repository = mock(ISkillCatalogRepository.class);
        SkillGovernanceState legacy = SkillGovernanceState.fromLegacy(
                "FROZEN", "FROZEN", "old freeze", "admin", LocalDateTime.now(CLOCK));
        SkillGovernanceState classified = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.LOCKED, SkillExecutionMode.ENABLED,
                SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.STABILITY_LOCK, "stable baseline", "admin", "approval-1",
                        LocalDateTime.now(CLOCK)), false);
        when(repository.available()).thenReturn(true);
        when(repository.find("PROJECT", "project-a", "skill-a", true))
                .thenReturn(Optional.of(entry(legacy)), Optional.of(entry(classified)));
        when(repository.compareAndSetGovernance(any())).thenReturn(true);
        SkillGovernanceTransitionUseCase useCase = new SkillGovernanceTransitionUseCase(
                repository, directTransaction(), mock(SkillGovernanceAuthorizationPort.class),
                mock(SkillGovernanceAuditPort.class), CLOCK);

        useCase.lock(command("approval-1"));

        ArgumentCaptor<SkillGovernanceUpdate> update = ArgumentCaptor.forClass(SkillGovernanceUpdate.class);
        verify(repository).compareAndSetGovernance(update.capture());
        assertEquals(SkillLockType.STABILITY_LOCK, update.getValue().nextState().lock().type());
        assertEquals(SkillExecutionMode.ENABLED, update.getValue().nextState().executionMode());
        assertEquals(false, update.getValue().nextState().legacyFrozenClassificationRequired());
    }

    @Test
    void legacyFrozenCanBeExplicitlyClassifiedAsIncidentQuarantine() {
        ISkillCatalogRepository repository = mock(ISkillCatalogRepository.class);
        SkillGovernanceState legacy = SkillGovernanceState.fromLegacy(
                "FROZEN", "FROZEN", "old freeze", "admin", LocalDateTime.now(CLOCK));
        SkillGovernanceState classified = new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.MANUAL_ONLY, SkillExecutionMode.QUARANTINED,
                SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.INCIDENT_QUARANTINE, "incident", "admin", "approval-1",
                        LocalDateTime.now(CLOCK)), false);
        when(repository.available()).thenReturn(true);
        when(repository.find("PROJECT", "project-a", "skill-a", true))
                .thenReturn(Optional.of(entry(legacy)), Optional.of(entry(classified)));
        when(repository.compareAndSetGovernance(any())).thenReturn(true);
        SkillGovernanceTransitionUseCase useCase = new SkillGovernanceTransitionUseCase(
                repository, directTransaction(), mock(SkillGovernanceAuthorizationPort.class),
                mock(SkillGovernanceAuditPort.class), CLOCK);

        useCase.quarantine(command("approval-1"));

        ArgumentCaptor<SkillGovernanceUpdate> update = ArgumentCaptor.forClass(SkillGovernanceUpdate.class);
        verify(repository).compareAndSetGovernance(update.capture());
        assertEquals(SkillLockType.INCIDENT_QUARANTINE, update.getValue().nextState().lock().type());
        assertEquals(SkillMutationMode.MANUAL_ONLY, update.getValue().nextState().mutationMode());
        assertEquals(SkillExecutionMode.QUARANTINED, update.getValue().nextState().executionMode());
        assertEquals(false, update.getValue().nextState().legacyFrozenClassificationRequired());
    }

    private SkillTransactionPort directTransaction() {
        return new SkillTransactionPort() {
            @Override
            public <T> T required(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
    }

    private SkillGovernanceCommand command(String approvalId) {
        return new SkillGovernanceCommand(
                "PROJECT", "project-a", "skill-a", 3, "hash-3",
                "approved transition", approvalId, "admin");
    }

    private SkillGovernanceState lockedEnabled() {
        return new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.LOCKED, SkillExecutionMode.ENABLED,
                SkillBindingMode.FLOATING,
                new SkillLock(SkillLockType.MANUAL_LOCK, "stability", "admin", "approval-0",
                        LocalDateTime.now(CLOCK)), false);
    }

    private SkillGovernanceState sealedEnabled() {
        return new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE, SkillMutationMode.SEALED, SkillExecutionMode.ENABLED,
                SkillBindingMode.PINNED,
                new SkillLock(SkillLockType.COMPLIANCE_SEAL, "baseline", "admin", "approval-0",
                        LocalDateTime.now(CLOCK)), false);
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
