package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;

public interface ChangePackageLandingGatePort {

    void requireLandingEnabled();

    default void requireLandingEnabled(ChangePackageLandingPlan plan) {
        requireLandingEnabled();
    }
}
