package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;

import java.util.List;

public interface ChangePackageLandingLockPort {

    List<String> acquire(String runId,
                         String leaseToken,
                         ChangePackageCurrent current,
                         ChangePackageLandingPlan plan,
                         String actor);

    void release(List<String> resourceKeys, String leaseToken);
}
