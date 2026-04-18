package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingProcessManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Repairs terminal LandingRun/package-pointer projection gaps without redispatching operations. */
@Service
public final class OpsLandingRunStateRecoveryService {

    private final ChangePackageLandingProcessManager landingProcessManager;
    private final boolean enabled;
    private final int batchSize;

    public OpsLandingRunStateRecoveryService(
            ChangePackageLandingProcessManager landingProcessManager,
            @Value("${orbisops.approved-landing.run-state-recovery.enabled:true}") boolean enabled,
            @Value("${orbisops.approved-landing.run-state-recovery.batch-size:50}") int batchSize) {
        if (landingProcessManager == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_PROCESS_MANAGER_REQUIRED");
        }
        this.landingProcessManager = landingProcessManager;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
    }

    @Scheduled(fixedDelayString = "${orbisops.approved-landing.run-state-recovery.fixed-delay-ms:15000}")
    public void recover() {
        if (!enabled) return;
        landingProcessManager.recoverStrandedTerminalRuns(batchSize, "landing-run-state-recovery");
        landingProcessManager.recoverLateSuccessfulCompletions(batchSize, "landing-late-completion-recovery");
    }
}
