package cn.lgs.orbisops.domain.skill.model;

import java.time.LocalDateTime;
import java.util.Locale;

/** Orthogonal governance facts. Mutation authority never controls runtime eligibility. */
public record SkillGovernanceState(SkillLifecycleStatus lifecycleStatus,
                                   SkillMutationMode mutationMode,
                                   SkillExecutionMode executionMode,
                                   SkillBindingMode bindingMode,
                                   SkillLock lock,
                                   boolean legacyFrozenClassificationRequired) {

    public SkillGovernanceState {
        lifecycleStatus = lifecycleStatus == null ? SkillLifecycleStatus.ACTIVE : lifecycleStatus;
        mutationMode = mutationMode == null ? SkillMutationMode.MANUAL_ONLY : mutationMode;
        executionMode = executionMode == null ? SkillExecutionMode.ENABLED : executionMode;
        bindingMode = bindingMode == null ? SkillBindingMode.FLOATING : bindingMode;
        lock = lock == null ? SkillLock.none() : lock;
        if (mutationMode == SkillMutationMode.SEALED && lock.type() == SkillLockType.NONE) {
            throw new IllegalArgumentException("SKILL_SEAL_LOCK_REQUIRED");
        }
        if (legacyFrozenClassificationRequired
                && (mutationMode != SkillMutationMode.LOCKED
                || executionMode != SkillExecutionMode.QUARANTINED
                || lock.type() != SkillLockType.LEGACY_UNCLASSIFIED)) {
            throw new IllegalArgumentException("SKILL_LEGACY_FROZEN_STATE_INVALID");
        }
    }

    public static SkillGovernanceState activeDefault() {
        return new SkillGovernanceState(
                SkillLifecycleStatus.ACTIVE,
                SkillMutationMode.MANUAL_ONLY,
                SkillExecutionMode.ENABLED,
                SkillBindingMode.FLOATING,
                SkillLock.none(),
                false);
    }

    /** Dual-read compatibility. A legacy FROZEN row is always migrated fail-closed. */
    public static SkillGovernanceState fromLegacy(String status,
                                                  String updateMode,
                                                  String frozenReason,
                                                  String frozenBy,
                                                  LocalDateTime frozenAt) {
        String legacyStatus = normalized(status, "ENABLED");
        String legacyMode = normalized(updateMode, "MANUAL_ONLY");
        boolean legacyFrozen = "FROZEN".equals(legacyStatus) || "FROZEN".equals(legacyMode);
        if (legacyFrozen) {
            String reason = text(frozenReason).isBlank()
                    ? "legacy FROZEN classification required" : text(frozenReason);
            String actor = text(frozenBy).isBlank() ? "SYSTEM_LEGACY_MIGRATION" : text(frozenBy);
            LocalDateTime lockedAt = frozenAt == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : frozenAt;
            return new SkillGovernanceState(
                    SkillLifecycleStatus.ACTIVE,
                    SkillMutationMode.LOCKED,
                    SkillExecutionMode.QUARANTINED,
                    SkillBindingMode.FLOATING,
                    new SkillLock(SkillLockType.LEGACY_UNCLASSIFIED, reason, actor, "", lockedAt),
                    true);
        }
        SkillLifecycleStatus lifecycle = switch (legacyStatus) {
            case "DRAFT" -> SkillLifecycleStatus.DRAFT;
            case "PAUSED" -> SkillLifecycleStatus.PAUSED;
            case "DEPRECATED" -> SkillLifecycleStatus.DEPRECATED;
            case "REJECTED" -> SkillLifecycleStatus.RETIRED;
            default -> SkillLifecycleStatus.ACTIVE;
        };
        SkillExecutionMode execution = switch (legacyStatus) {
            case "DISABLED", "DRAFT", "PAUSED", "DEPRECATED", "REJECTED" -> SkillExecutionMode.DISABLED;
            default -> SkillExecutionMode.ENABLED;
        };
        SkillMutationMode mutation = switch (legacyMode) {
            case "AUTO" -> SkillMutationMode.AUTO;
            case "LOCKED" -> SkillMutationMode.LOCKED;
            case "SEALED" -> SkillMutationMode.SEALED;
            default -> SkillMutationMode.MANUAL_ONLY;
        };
        SkillLock lock = switch (mutation) {
            case LOCKED -> new SkillLock(
                    SkillLockType.MANUAL_LOCK,
                    text(frozenReason).isBlank() ? "compatibility lock" : text(frozenReason),
                    text(frozenBy).isBlank() ? "SYSTEM_GOVERNANCE" : text(frozenBy),
                    "",
                    frozenAt == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : frozenAt);
            case SEALED -> new SkillLock(
                    SkillLockType.COMPLIANCE_SEAL,
                    text(frozenReason).isBlank() ? "compatibility seal" : text(frozenReason),
                    text(frozenBy).isBlank() ? "SYSTEM_GOVERNANCE" : text(frozenBy),
                    "",
                    frozenAt == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : frozenAt);
            default -> SkillLock.none();
        };
        return new SkillGovernanceState(
                lifecycle, mutation, execution, SkillBindingMode.FLOATING, lock, false);
    }

    public boolean activeAtUse() {
        return lifecycleStatus == SkillLifecycleStatus.ACTIVE && executionMode.formalRuntimeEnabled();
    }

    public boolean ordinaryMutationAllowed() {
        return mutationMode.ordinaryMutationAllowed();
    }

    public boolean autoPublishAllowed() {
        return lifecycleStatus == SkillLifecycleStatus.ACTIVE
                && mutationMode.autoPublishAllowed()
                && (executionMode == SkillExecutionMode.ENABLED
                || executionMode == SkillExecutionMode.SHADOW_ONLY)
                && !legacyFrozenClassificationRequired;
    }

    public SkillGovernanceState withMutation(SkillMutationMode mode, SkillLock nextLock) {
        return new SkillGovernanceState(
                lifecycleStatus, mode, executionMode, bindingMode, nextLock, false);
    }

    public SkillGovernanceState withExecution(SkillExecutionMode mode, SkillLock nextLock) {
        return new SkillGovernanceState(
                lifecycleStatus, mutationMode, mode, bindingMode,
                nextLock == null ? lock : nextLock, false);
    }

    public SkillGovernanceState withLifecycle(SkillLifecycleStatus status) {
        return new SkillGovernanceState(
                status, mutationMode, executionMode, bindingMode, lock,
                legacyFrozenClassificationRequired);
    }

    public String legacyStatusProjection() {
        if (lifecycleStatus == SkillLifecycleStatus.ACTIVE) {
            return executionMode == SkillExecutionMode.DISABLED ? "DISABLED" : "ENABLED";
        }
        return lifecycleStatus.name();
    }

    public String legacyUpdateModeProjection() {
        return mutationMode.name();
    }

    private static String normalized(String value, String fallback) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
