package cn.lgs.orbisops.trigger.application.skill;

/** Log only our fixed machine codes; never a provider body, SQL, URL or credential. */
final class OpsSkillBackgroundFailure {
    private OpsSkillBackgroundFailure() {}
    static String code(RuntimeException error) {
        String message=error.getMessage();
        return message!=null && message.matches("SKILL_[A-Z0-9_]{1,100}")
                ? message : error.getClass().getSimpleName();
    }
}
