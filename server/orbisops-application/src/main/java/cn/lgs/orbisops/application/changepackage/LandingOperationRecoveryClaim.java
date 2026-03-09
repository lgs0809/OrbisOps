package cn.lgs.orbisops.application.changepackage;

/** Exclusive reconciliation lease for one expired UNKNOWN operation. */
public record LandingOperationRecoveryClaim(
        LandingOperationRecoveryCandidate candidate,
        long stateVersion,
        String workerId) {
}
