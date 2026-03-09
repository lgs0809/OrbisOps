package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;

import java.util.Set;

public final class ChangePackageStatusPolicy {

    private static final Set<ChangePackageStatus> PENDING_REVIEW = Set.of(
            ChangePackageStatus.READY_FOR_REVIEW,
            ChangePackageStatus.REVIEWING);

    private static final Set<ChangePackageStatus> FAILED = Set.of(
            ChangePackageStatus.VALIDATION_FAILED,
            ChangePackageStatus.NEEDS_REPLAN,
            ChangePackageStatus.LANDING_FAILED);

    private static final Set<ChangePackageStatus> RUNNING = Set.of(
            ChangePackageStatus.VALIDATING,
            ChangePackageStatus.LANDING_RUNNING);

    private static final Set<ChangePackageStatus> ACTIVE_EXECUTION = Set.of(
            ChangePackageStatus.REVIEWING,
            ChangePackageStatus.REVISING,
            ChangePackageStatus.APPROVED,
            ChangePackageStatus.LANDING_RUNNING,
            ChangePackageStatus.LANDING_FAILED,
            ChangePackageStatus.NEEDS_REPLAN);

    public boolean pendingReview(String status) {
        return contains(PENDING_REVIEW, status);
    }

    public boolean failed(String status) {
        return contains(FAILED, status);
    }

    public boolean running(String status) {
        return contains(RUNNING, status);
    }

    public boolean activeExecution(String status) {
        return contains(ACTIVE_EXECUTION, status);
    }

    /** Stable product queue projection shared by all consoles. */
    public String productQueue(String status) {
        ChangePackageStatus value;
        try {
            value = ChangePackageStatus.require(status);
        } catch (RuntimeException ignored) {
            return "HISTORY";
        }
        return switch (value) {
            case DRAFT, VALIDATING, READY_FOR_REVIEW, REVIEWING, REVISING -> "PENDING";
            case APPROVED, LANDING_RUNNING -> "RUNNING";
            case VALIDATION_FAILED, REJECTED, LANDING_FAILED, NEEDS_REPLAN -> "INTERVENTION";
            case LANDED, CLOSED -> "HISTORY";
        };
    }

    private boolean contains(Set<ChangePackageStatus> candidates, String status) {
        try {
            return candidates.contains(ChangePackageStatus.require(status));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
