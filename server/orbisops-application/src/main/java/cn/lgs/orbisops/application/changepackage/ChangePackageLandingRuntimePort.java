package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

public interface ChangePackageLandingRuntimePort {

    /** Read-only, independently persisted postconditions. Never re-enter the agent or replay writes. */
    default java.util.Map<String, Object> verifyCompletedOperations(ChangePackageCurrent current,
            ChangePackageVersion approvedVersion, ChangePackageLandingPlan plan, String landingRunId,
            String actor, java.util.List<LandingOperationFact> facts) {
        throw new IllegalStateException("LANDING_INDEPENDENT_VERIFIER_UNAVAILABLE");
    }

    ChangePackageLandingRuntimeResult execute(ChangePackageCurrent current,
                                              ChangePackageVersion approvedVersion,
                                              ChangePackageLandingPlan plan,
                                              ChangePackageLandingRequest request,
                                              String landingRunId,
                                              String actor);
}
