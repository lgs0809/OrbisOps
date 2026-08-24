package cn.lgs.orbisops.application.skill;

/** Current project governance revoked a previously selected Skill. */
public final class SkillRuntimeAccessRevokedException extends SecurityException {
    public SkillRuntimeAccessRevokedException() {
        super("SKILL_RUNTIME_ACCESS_REVOKED");
    }
}
