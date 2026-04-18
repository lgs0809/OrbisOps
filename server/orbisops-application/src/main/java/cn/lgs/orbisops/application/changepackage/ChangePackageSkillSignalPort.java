package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

public interface ChangePackageSkillSignalPort {

    void recordAccepted(ChangePackageCurrent current, ChangePackageVersion approvedVersion, String actor);

    void reconcileApprovedOutcome(ChangePackageVersion approvedVersion);
}
