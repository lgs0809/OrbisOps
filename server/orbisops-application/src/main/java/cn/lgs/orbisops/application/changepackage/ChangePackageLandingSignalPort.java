package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

import java.util.Map;

public interface ChangePackageLandingSignalPort {

    void recordOutcome(ChangePackageCurrent current,
                       ChangePackageVersion approvedVersion,
                       String landingRunId,
                       String status,
                       Map<String, Object> result);
}
