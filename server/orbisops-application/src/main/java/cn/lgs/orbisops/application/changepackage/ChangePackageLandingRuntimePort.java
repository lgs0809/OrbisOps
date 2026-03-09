package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingRequest;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

public interface ChangePackageLandingRuntimePort {

    ChangePackageLandingRuntimeResult execute(ChangePackageCurrent current,
                                              ChangePackageVersion approvedVersion,
                                              ChangePackageLandingPlan plan,
                                              ChangePackageLandingRequest request,
                                              String landingRunId,
                                              String actor);
}
