package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingGatePort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpsChangePackageLandingGateAdapter implements ChangePackageLandingGatePort {

    private final OpsChangePackageLandingSettings settings;

    public OpsChangePackageLandingGateAdapter() {
        this(OpsChangePackageLandingSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsChangePackageLandingGateAdapter(
            OpsChangePackageLandingSettings settings) {
        this.settings = settings == null
                ? OpsChangePackageLandingSettings.defaults()
                : settings;
    }

    @Override
    public void requireLandingEnabled() {
        if (!settings.enabled()) {
            throw new IllegalStateException(
                    "APPROVED_LANDING_DISABLED：当前环境只允许调查、验证和 ChangePackage 审核，生产自动 Landing 未开放");
        }
    }

    @Override
    public void requireLandingEnabled(ChangePackageLandingPlan plan) {
        requireLandingEnabled();
    }

    public boolean enabled() {
        return settings.enabled();
    }
}
