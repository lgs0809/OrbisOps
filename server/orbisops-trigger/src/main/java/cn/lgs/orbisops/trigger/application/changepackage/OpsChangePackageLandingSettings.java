package cn.lgs.orbisops.trigger.application.changepackage;

/** Typed environment gate for approved production Landing. */
public record OpsChangePackageLandingSettings(boolean enabled) {

    public static OpsChangePackageLandingSettings defaults() {
        return new OpsChangePackageLandingSettings(false);
    }

    static OpsChangePackageLandingSettings legacyConstructorDefaults() {
        return defaults();
    }
}
