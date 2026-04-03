package cn.lgs.orbisops.domain.runtime.llm.model;

/** Structured-call and degradation settings governing shared runtime LLM access. */
public record LlmRuntimeSettings(
        boolean failOnLlmDegradation,
        boolean jsonRepairRetryEnabled,
        boolean jsonResponseFormatEnabled,
        int jsonMaxCompletionTokens,
        boolean jsonSkillContextRetryEnabled,
        int jsonSkillContextRetryMaxChars,
        int modelCallTimeoutSeconds) {

    public LlmRuntimeSettings {
        jsonMaxCompletionTokens = bounded(
                jsonMaxCompletionTokens, 0, 100_000, 1_200);
        jsonSkillContextRetryMaxChars = bounded(
                jsonSkillContextRetryMaxChars, 0, 200_000, 16_000);
        modelCallTimeoutSeconds = bounded(
                modelCallTimeoutSeconds, 1, 3_600, 240);
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
