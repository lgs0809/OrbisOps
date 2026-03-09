package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageStateMachineTest {

    private final ChangePackageStateMachine stateMachine = new ChangePackageStateMachine();

    @Test
    void supportsPreApprovalReviewAndLandingHappyPath() {
        assertDoesNotThrow(() -> stateMachine.requireTransition("DRAFT", "READY_FOR_REVIEW"));
        assertDoesNotThrow(() -> stateMachine.requireTransition("READY_FOR_REVIEW", "REVIEWING"));
        assertDoesNotThrow(() -> stateMachine.requireTransition("REVIEWING", "APPROVED"));
        assertDoesNotThrow(() -> stateMachine.requireTransition("APPROVED", "LANDING_RUNNING"));
        assertDoesNotThrow(() -> stateMachine.requireTransition("LANDING_RUNNING", "LANDED"));
    }

    @Test
    void supportsFailClosedRevalidationBeforeReviewSubmission() {
        assertDoesNotThrow(() -> stateMachine.requireTransition("READY_FOR_REVIEW", "READY_FOR_REVIEW"));
        assertDoesNotThrow(() -> stateMachine.requireTransition("READY_FOR_REVIEW", "VALIDATION_FAILED"));
    }

    @Test
    void landingFailedCanReturnToLandingRunningOnlyForProcessManagerGuardedRetry() {
        assertTrue(stateMachine.canTransition(ChangePackageStatus.LANDING_FAILED, ChangePackageStatus.LANDING_RUNNING));
        assertTrue(stateMachine.canTransition(ChangePackageStatus.LANDING_FAILED, ChangePackageStatus.REVISING));
        assertFalse(stateMachine.canTransition(ChangePackageStatus.LANDING_FAILED, ChangePackageStatus.LANDED));
    }

    @Test
    void supportsReplanOnlyByReturningToRevision() {
        assertTrue(stateMachine.canTransition(ChangePackageStatus.NEEDS_REPLAN, ChangePackageStatus.REVISING));
        assertFalse(stateMachine.canTransition(ChangePackageStatus.NEEDS_REPLAN, ChangePackageStatus.LANDED));
        assertThrows(IllegalStateException.class,
                () -> stateMachine.requireTransition("NEEDS_REPLAN", "LANDED"));
    }

    @Test
    void blocksDirectApprovalAndRevisionAfterApprovalOrLanding() {
        assertThrows(IllegalStateException.class,
                () -> stateMachine.requireTransition("DRAFT", "APPROVED"));
        assertThrows(IllegalStateException.class,
                () -> stateMachine.requireTransition("READY_FOR_REVIEW", "APPROVED"));
        assertThrows(IllegalStateException.class,
                () -> stateMachine.requireTransition("APPROVED", "REVISING"));
        assertThrows(IllegalStateException.class,
                () -> stateMachine.requireTransition("LANDED", "REVISING"));
    }

    @Test
    void rejectsUnknownStatusInsteadOfNormalizingSecurityDecision() {
        assertThrows(IllegalArgumentException.class,
                () -> stateMachine.requireTransition("UNKNOWN", "REVIEWING"));
    }
}
