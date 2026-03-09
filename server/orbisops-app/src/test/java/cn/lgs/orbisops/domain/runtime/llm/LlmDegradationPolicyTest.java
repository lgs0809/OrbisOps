package cn.lgs.orbisops.domain.runtime.llm;

import cn.lgs.orbisops.domain.runtime.llm.model.LlmDegradationMode;
import cn.lgs.orbisops.domain.runtime.llm.model.LlmRuntimeSettings;
import cn.lgs.orbisops.domain.runtime.llm.service.LlmDegradationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmDegradationPolicyTest {

    private final LlmDegradationPolicy policy = new LlmDegradationPolicy();

    @Test
    void failClosedIsSelectedWhenConfiguredOrSettingsAreMissing() {
        assertEquals(LlmDegradationMode.FAIL_CLOSED, policy.decide(null));
        assertEquals(
                LlmDegradationMode.FAIL_CLOSED,
                policy.decide(settings(true)));
    }

    @Test
    void deterministicRuleFallbackMustBeExplicitlyEnabled() {
        assertEquals(
                LlmDegradationMode.RULE_FALLBACK,
                policy.decide(settings(false)));
    }

    @Test
    void runtimeSettingsNormalizeUnsafeNumericLimits() {
        LlmRuntimeSettings settings = new LlmRuntimeSettings(
                false,
                true,
                true,
                -1,
                true,
                300_000,
                0);

        assertEquals(1_200, settings.jsonMaxCompletionTokens());
        assertEquals(16_000, settings.jsonSkillContextRetryMaxChars());
        assertEquals(240, settings.modelCallTimeoutSeconds());
    }

    private LlmRuntimeSettings settings(boolean failClosed) {
        return new LlmRuntimeSettings(
                failClosed,
                true,
                true,
                1_200,
                true,
                16_000,
                240);
    }
}
