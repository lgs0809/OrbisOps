package cn.lgs.orbisops.trigger.ops;

/** Skill integrity, authority or budget failures must not become an optional model degradation. */
public final class OpsLlmSkillContextException extends IllegalStateException {
    public OpsLlmSkillContextException(String reason) { super(reason); }
    public OpsLlmSkillContextException(RuntimeException cause) { super("SKILL_RUNTIME_CONTEXT_FAILED", cause); }
}
