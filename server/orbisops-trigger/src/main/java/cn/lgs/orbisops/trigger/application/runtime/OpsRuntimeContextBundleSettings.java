package cn.lgs.orbisops.trigger.application.runtime;

/** Typed limits used when creating immutable runtime context bundles. */
public record OpsRuntimeContextBundleSettings(int selectedSkillLimit) {

    public OpsRuntimeContextBundleSettings {
        selectedSkillLimit = Math.max(1, Math.min(selectedSkillLimit, 100));
    }

    public static OpsRuntimeContextBundleSettings defaults() {
        return new OpsRuntimeContextBundleSettings(6);
    }
}
