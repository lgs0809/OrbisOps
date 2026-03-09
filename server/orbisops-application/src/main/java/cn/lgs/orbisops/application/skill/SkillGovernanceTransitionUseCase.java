package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceState;
import cn.lgs.orbisops.domain.skill.model.SkillGovernanceUpdate;
import cn.lgs.orbisops.domain.skill.model.SkillLock;
import cn.lgs.orbisops.domain.skill.model.SkillLockType;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Function;

/** Narrow application boundary for governance-only transitions; package identity is unchanged. */
public final class SkillGovernanceTransitionUseCase {

    private final ISkillCatalogRepository catalogRepository;
    private final SkillTransactionPort transactionPort;
    private final SkillGovernanceAuthorizationPort authorizationPort;
    private final SkillGovernanceAuditPort auditPort;
    private final Clock clock;

    public SkillGovernanceTransitionUseCase(ISkillCatalogRepository catalogRepository,
                                            SkillTransactionPort transactionPort,
                                            SkillGovernanceAuthorizationPort authorizationPort,
                                            SkillGovernanceAuditPort auditPort) {
        this(catalogRepository, transactionPort, authorizationPort, auditPort, Clock.systemUTC());
    }

    public SkillGovernanceTransitionUseCase(ISkillCatalogRepository catalogRepository,
                                            SkillTransactionPort transactionPort,
                                            SkillGovernanceAuthorizationPort authorizationPort,
                                            SkillGovernanceAuditPort auditPort,
                                            Clock clock) {
        if (catalogRepository == null) throw new IllegalArgumentException("SKILL_CATALOG_REPOSITORY_REQUIRED");
        if (transactionPort == null) throw new IllegalArgumentException("SKILL_TRANSACTION_PORT_REQUIRED");
        if (authorizationPort == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_AUTHORIZATION_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_AUDIT_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_CLOCK_REQUIRED");
        this.catalogRepository = catalogRepository;
        this.transactionPort = transactionPort;
        this.authorizationPort = authorizationPort;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    public SkillCatalogEntry lock(SkillGovernanceCommand command) {
        return transition(command, SkillGovernancePermission.SKILL_LOCK, "LOCK", current -> {
            if (current.legacyFrozenClassificationRequired()) {
                return new SkillGovernanceState(
                        current.lifecycleStatus(), SkillMutationMode.LOCKED, SkillExecutionMode.ENABLED,
                        current.bindingMode(),
                        new SkillLock(SkillLockType.STABILITY_LOCK, command.reason(), command.actor(),
                                command.approvalId(), LocalDateTime.now(clock)),
                        false);
            }
            if (current.mutationMode() == SkillMutationMode.SEALED) {
                throw new IllegalStateException("SKILL_LOCK_FORBIDDEN_WHILE_SEALED");
            }
            if (current.mutationMode() == SkillMutationMode.LOCKED) {
                throw new IllegalStateException("SKILL_ALREADY_LOCKED");
            }
            return new SkillGovernanceState(
                    current.lifecycleStatus(), SkillMutationMode.LOCKED, current.executionMode(),
                    current.bindingMode(),
                    new SkillLock(SkillLockType.MANUAL_LOCK, command.reason(), command.actor(),
                            command.approvalId(), LocalDateTime.now(clock)),
                    false);
        });
    }

    public SkillCatalogEntry unlock(SkillGovernanceCommand command) {
        command.requireApproval();
        return transition(command, SkillGovernancePermission.SKILL_UNLOCK, "UNLOCK", current -> {
            requireClassified(current);
            if (current.mutationMode() == SkillMutationMode.SEALED) {
                throw new IllegalStateException("SKILL_UNLOCK_FORBIDDEN_WHILE_SEALED");
            }
            if (current.mutationMode() != SkillMutationMode.LOCKED) {
                throw new IllegalStateException("SKILL_UNLOCK_REQUIRES_LOCKED");
            }
            return new SkillGovernanceState(
                    current.lifecycleStatus(), SkillMutationMode.MANUAL_ONLY, current.executionMode(),
                    current.bindingMode(), SkillLock.none(), false);
        });
    }

    public SkillCatalogEntry seal(SkillGovernanceCommand command) {
        command.requireApproval();
        return transition(command, SkillGovernancePermission.SKILL_SEAL, "SEAL", current -> {
            requireClassified(current);
            if (current.mutationMode() == SkillMutationMode.SEALED) {
                throw new IllegalStateException("SKILL_ALREADY_SEALED");
            }
            return new SkillGovernanceState(
                    current.lifecycleStatus(), SkillMutationMode.SEALED, current.executionMode(),
                    current.bindingMode(),
                    new SkillLock(SkillLockType.COMPLIANCE_SEAL, command.reason(), command.actor(),
                            command.approvalId(), LocalDateTime.now(clock)),
                    false);
        });
    }

    public SkillCatalogEntry quarantine(SkillGovernanceCommand command) {
        return transition(command, SkillGovernancePermission.SKILL_QUARANTINE, "QUARANTINE", current -> {
            if (current.legacyFrozenClassificationRequired()) {
                return new SkillGovernanceState(
                        current.lifecycleStatus(), SkillMutationMode.MANUAL_ONLY,
                        SkillExecutionMode.QUARANTINED, current.bindingMode(),
                        new SkillLock(SkillLockType.INCIDENT_QUARANTINE, command.reason(), command.actor(),
                                command.approvalId(), LocalDateTime.now(clock)),
                        false);
            }
            SkillLock lock = current.mutationMode() == SkillMutationMode.SEALED
                    ? current.lock()
                    : new SkillLock(SkillLockType.INCIDENT_QUARANTINE, command.reason(), command.actor(),
                            command.approvalId(), LocalDateTime.now(clock));
            return new SkillGovernanceState(
                    current.lifecycleStatus(), current.mutationMode(), SkillExecutionMode.QUARANTINED,
                    current.bindingMode(), lock, false);
        });
    }

    private SkillCatalogEntry transition(SkillGovernanceCommand command,
                                         SkillGovernancePermission permission,
                                         String action,
                                         Function<SkillGovernanceState, SkillGovernanceState> transition) {
        authorizationPort.require(command.actor(), permission,
                command.scope(), command.projectId(), command.skillId());
        return transactionPort.required(() -> {
            requireStore();
            SkillCatalogEntry current = catalogRepository.find(
                            command.scope(), command.projectId(), command.skillId(), true)
                    .orElseThrow(() -> new IllegalArgumentException("Skill 不存在：" + command.skillId()));
            requireExpectedPointer(command, current);
            SkillGovernanceState before = current.governanceState();
            SkillGovernanceState after = transition.apply(before);
            boolean changed = catalogRepository.compareAndSetGovernance(new SkillGovernanceUpdate(
                    command.scope(), command.projectId(), command.skillId(),
                    command.expectedVersion(), command.expectedSkillHash(), before, after));
            if (!changed) throw new IllegalStateException("SKILL_GOVERNANCE_CAS_CONFLICT:" + command.skillId());
            auditPort.record(new SkillGovernanceAuditRecord(
                    action, command.scope(), command.projectId(), command.skillId(), command.actor(),
                    command.reason(), command.approvalId(), before, after, LocalDateTime.now(clock)));
            return catalogRepository.find(command.scope(), command.projectId(), command.skillId(), true)
                    .orElseThrow(() -> new IllegalStateException("SKILL_GOVERNANCE_POST_STATE_MISSING"));
        });
    }

    private void requireExpectedPointer(SkillGovernanceCommand command, SkillCatalogEntry current) {
        if (current.currentVersion() != command.expectedVersion()
                || !current.currentSkillHash().equals(command.expectedSkillHash())) {
            throw new IllegalStateException("SKILL_CURRENT_POINTER_CONFLICT:" + command.skillId());
        }
    }

    private void requireClassified(SkillGovernanceState current) {
        if (current.legacyFrozenClassificationRequired()) {
            throw new IllegalStateException("SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED");
        }
    }

    private void requireStore() {
        if (!catalogRepository.available()) throw new IllegalStateException("SKILL_CATALOG_STORE_UNAVAILABLE");
    }
}
