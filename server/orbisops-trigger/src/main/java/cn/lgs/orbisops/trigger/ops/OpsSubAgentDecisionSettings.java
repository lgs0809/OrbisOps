package cn.lgs.orbisops.trigger.ops;

/** Typed switches for SubAgent THINK and REVIEW LLM participation. */
public record OpsSubAgentDecisionSettings(
        boolean decisionLlmEnabled,
        boolean reflectionLlmEnabled) {

    public static OpsSubAgentDecisionSettings defaults() {
        return new OpsSubAgentDecisionSettings(true, true);
    }

    static OpsSubAgentDecisionSettings legacyConstructorDefaults() {
        return new OpsSubAgentDecisionSettings(false, false);
    }
}
