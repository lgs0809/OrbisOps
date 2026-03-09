package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.runtime.llm.model.LlmRuntimeSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentLlmSettingsTest {

    @Test
    void normalizesUnsafeNumericLimitsAndPreservesPolicies() {
        OpsAgentLlmSettings settings = new OpsAgentLlmSettings(
                false,
                1,
                0,
                false,
                -1,
                true,
                false,
                false,
                -1,
                false,
                300_000);

        assertFalse(settings.enabled());
        assertEquals(6_000, settings.maxOutputChars());
        assertEquals(240, settings.modelCallTimeoutSeconds());
        assertFalse(settings.skillContextEnabled());
        assertEquals(12_000, settings.skillContextMaxChars());
        assertTrue(settings.failOnLlmDegradation());
        assertFalse(settings.jsonRepairRetryEnabled());
        assertFalse(settings.jsonResponseFormatEnabled());
        assertEquals(1_200, settings.jsonMaxCompletionTokens());
        assertFalse(settings.jsonSkillContextRetryEnabled());
        assertEquals(16_000, settings.jsonSkillContextRetryMaxChars());
    }

    @Test
    void projectsRuntimePolicySettingsWithoutDuplicatingConfiguration() {
        OpsAgentLlmSettings settings = new OpsAgentLlmSettings(
                true, 8_000, 90, true, 20_000, true,
                false, false, 2_000, false, 24_000);

        LlmRuntimeSettings policy = settings.policySettings();

        assertTrue(policy.failOnLlmDegradation());
        assertFalse(policy.jsonRepairRetryEnabled());
        assertFalse(policy.jsonResponseFormatEnabled());
        assertEquals(2_000, policy.jsonMaxCompletionTokens());
        assertFalse(policy.jsonSkillContextRetryEnabled());
        assertEquals(24_000, policy.jsonSkillContextRetryMaxChars());
        assertEquals(90, policy.modelCallTimeoutSeconds());
    }
}
