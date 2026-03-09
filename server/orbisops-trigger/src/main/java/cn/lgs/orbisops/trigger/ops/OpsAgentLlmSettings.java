package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.runtime.llm.model.LlmRuntimeSettings;

/** Typed settings for shared operations LLM access and structured-call behavior. */
public record OpsAgentLlmSettings(
        boolean enabled,
        int maxOutputChars,
        int modelCallTimeoutSeconds,
        boolean skillContextEnabled,
        int skillContextMaxChars,
        boolean failOnLlmDegradation,
        boolean jsonRepairRetryEnabled,
        boolean jsonResponseFormatEnabled,
        int jsonMaxCompletionTokens,
        boolean jsonSkillContextRetryEnabled,
        int jsonSkillContextRetryMaxChars) {

    public OpsAgentLlmSettings {
        maxOutputChars = bounded(maxOutputChars, 256, 100_000, 6_000);
        modelCallTimeoutSeconds = bounded(modelCallTimeoutSeconds, 1, 3_600, 240);
        skillContextMaxChars = bounded(skillContextMaxChars, 0, 200_000, 12_000);
        jsonMaxCompletionTokens = bounded(jsonMaxCompletionTokens, 0, 100_000, 1_200);
        jsonSkillContextRetryMaxChars = bounded(
                jsonSkillContextRetryMaxChars,
                0,
                200_000,
                16_000);
    }

    public static OpsAgentLlmSettings defaults() {
        return new OpsAgentLlmSettings(
                true,
                6_000,
                240,
                true,
                12_000,
                false,
                true,
                true,
                1_200,
                true,
                16_000);
    }

    LlmRuntimeSettings policySettings() {
        return new LlmRuntimeSettings(
                failOnLlmDegradation,
                jsonRepairRetryEnabled,
                jsonResponseFormatEnabled,
                jsonMaxCompletionTokens,
                jsonSkillContextRetryEnabled,
                jsonSkillContextRetryMaxChars,
                modelCallTimeoutSeconds);
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
