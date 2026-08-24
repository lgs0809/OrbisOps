package cn.lgs.orbisops.application.skill;

/** A transient provider request failure; the durable worker owns backoff, not the HTTP client. */
public final class SkillModelTransportDeferredException extends RuntimeException {
    public SkillModelTransportDeferredException(Throwable cause) {
        super("SKILL_MODEL_TRANSPORT_DEFERRED", cause);
    }
}
