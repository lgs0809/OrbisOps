package cn.lgs.orbisops.domain.changepackage.model;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackageStateMachine;

/**
 * Aggregate root for the authoritative ChangePackage pointer.
 *
 * Snapshot contents are versioned separately; this aggregate owns the mutable
 * lifecycle pointer and protects status, version, hash and approval invariants.
 */
public final class ChangePackageAggregate {

    private static final ChangePackageStateMachine STATE_MACHINE = new ChangePackageStateMachine();

    private ChangePackagePointer pointer;

    private ChangePackageAggregate(ChangePackagePointer pointer) {
        if (pointer == null) throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_REQUIRED");
        this.pointer = pointer;
    }

    public static ChangePackageAggregate rehydrate(ChangePackagePointer pointer) {
        return new ChangePackageAggregate(pointer);
    }

    public ChangePackagePointer pointer() {
        return pointer;
    }

    public ChangePackagePointer revise(int nextVersion, String nextHash) {
        requireNextVersion(nextVersion);
        requireRevision();
        pointer = new ChangePackagePointer(pointer.packageId(), ChangePackageStatus.REVISING,
                nextVersion, requiredHash(nextHash), 0, "");
        return pointer;
    }

    public void requireRevision() {
        STATE_MACHINE.requireTransition(pointer.status(), ChangePackageStatus.REVISING);
    }

    public ChangePackagePointer validationPassed(int nextVersion, String nextHash) {
        requireNextVersion(nextVersion);
        STATE_MACHINE.requireTransition(pointer.status(), ChangePackageStatus.READY_FOR_REVIEW);
        pointer = new ChangePackagePointer(pointer.packageId(), ChangePackageStatus.READY_FOR_REVIEW,
                nextVersion, requiredHash(nextHash), 0, "");
        return pointer;
    }

    public void requireValidationOutcome(boolean passed) {
        STATE_MACHINE.requireTransition(pointer.status(), passed
                ? ChangePackageStatus.READY_FOR_REVIEW
                : ChangePackageStatus.VALIDATION_FAILED);
    }

    public ChangePackagePointer validationFailed() {
        requireValidationOutcome(false);
        pointer = pointer.withStatus(ChangePackageStatus.VALIDATION_FAILED);
        return pointer;
    }

    public ChangePackagePointer submitReview() {
        return transitionWithoutVersion(ChangePackageStatus.REVIEWING);
    }

    public ChangePackagePointer reject() {
        return transitionWithoutVersion(ChangePackageStatus.REJECTED);
    }

    public void requireApprovable(int version, String packageHash) {
        STATE_MACHINE.requireTransition(pointer.status(), ChangePackageStatus.APPROVED);
        if (version != pointer.version()) {
            throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_VERSION_MISMATCH:current="
                    + pointer.version() + ",submitted=" + version);
        }
        String submittedHash = requiredHash(packageHash);
        if (!pointer.packageHash().equals(submittedHash)) {
            throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_HASH_MISMATCH");
        }
    }

    public ChangePackagePointer approve(int version, String packageHash) {
        requireApprovable(version, packageHash);
        pointer = new ChangePackagePointer(pointer.packageId(), ChangePackageStatus.APPROVED,
                pointer.version(), pointer.packageHash(), version, requiredHash(packageHash));
        return pointer;
    }

    public ChangePackagePointer startLanding() {
        STATE_MACHINE.requireTransition(pointer.status(), ChangePackageStatus.LANDING_RUNNING);
        if (!pointer.approved()) {
            throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_REQUIRED");
        }
        pointer = pointer.withStatus(ChangePackageStatus.LANDING_RUNNING);
        return pointer;
    }

    public ChangePackagePointer finishLanding(ChangePackageStatus outcome) {
        requireLandingOutcome(outcome);
        STATE_MACHINE.requireTransition(pointer.status(), outcome);
        pointer = pointer.withStatus(outcome);
        return pointer;
    }

    /** Authoritative recovery may close a crashed run from its persisted package state. */
    public ChangePackagePointer reconcileLanding(ChangePackageStatus outcome) {
        requireLandingOutcome(outcome);
        if (!pointer.approved()) {
            throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_REQUIRED");
        }
        if (pointer.status() == ChangePackageStatus.APPROVED) {
            startLanding();
            return finishLanding(outcome);
        }
        if (pointer.status() == ChangePackageStatus.LANDING_RUNNING) {
            return finishLanding(outcome);
        }
        if (pointer.status() == ChangePackageStatus.LANDING_FAILED) {
            pointer = pointer.withStatus(outcome);
            return pointer;
        }
        throw new IllegalStateException("CHANGE_PACKAGE_LANDING_RECOVERY_STATE_INVALID:" + pointer.status());
    }

    private void requireLandingOutcome(ChangePackageStatus outcome) {
        if (outcome != ChangePackageStatus.LANDED
                && outcome != ChangePackageStatus.LANDING_FAILED
                && outcome != ChangePackageStatus.NEEDS_REPLAN) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_OUTCOME_INVALID:"
                    + (outcome == null ? "UNKNOWN" : outcome.name()));
        }
    }

    private ChangePackagePointer transitionWithoutVersion(ChangePackageStatus target) {
        STATE_MACHINE.requireTransition(pointer.status(), target);
        pointer = pointer.withStatus(target);
        return pointer;
    }

    private void requireNextVersion(int nextVersion) {
        if (nextVersion != pointer.version() + 1) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_NEXT_VERSION_INVALID:current="
                    + pointer.version() + ",next=" + nextVersion);
        }
    }

    private String requiredHash(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("CHANGE_PACKAGE_HASH_REQUIRED");
        return normalized;
    }
}
