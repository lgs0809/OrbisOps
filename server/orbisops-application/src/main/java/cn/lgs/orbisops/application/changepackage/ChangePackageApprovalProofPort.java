package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;

public interface ChangePackageApprovalProofPort {

    void verifyBeforeApprove(ChangePackageCurrent current, ChangePackageVersion version);
}
