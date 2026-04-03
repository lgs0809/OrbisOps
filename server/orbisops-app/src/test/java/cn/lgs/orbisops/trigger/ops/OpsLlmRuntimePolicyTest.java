package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.runtime.llm.model.LlmRuntimeSettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmRuntimePolicyTest {

    private final OpsLlmRuntimePolicy policy = new OpsLlmRuntimePolicy();

    @Test
    void statusExtendsBaseStatusInPlaceWithRuntimeSettings() {
        Map<String, Object> baseStatus = new LinkedHashMap<>();
        baseStatus.put("enabled", true);
        baseStatus.put("chatAvailable", false);

        Map<String, Object> result = policy.status(baseStatus, settings(false));

        assertSame(baseStatus, result);
        assertEquals(Boolean.TRUE, result.get("enabled"));
        assertEquals(Boolean.FALSE, result.get("chatAvailable"));
        assertEquals(Boolean.FALSE, result.get("failOnLlmDegradation"));
        assertEquals(Boolean.TRUE, result.get("jsonRepairRetryEnabled"));
        assertEquals(Boolean.FALSE, result.get("jsonResponseFormatEnabled"));
        assertEquals(1_234, result.get("jsonMaxCompletionTokens"));
        assertEquals(Boolean.TRUE, result.get("jsonSkillContextRetryEnabled"));
        assertEquals(16_234, result.get("jsonSkillContextRetryMaxChars"));
        assertEquals(234, result.get("modelCallTimeoutSeconds"));
        assertEquals("RULE_FALLBACK", result.get("degradationMode"));
        assertEquals(List.of(
                        "enabled",
                        "chatAvailable",
                        "failOnLlmDegradation",
                        "jsonRepairRetryEnabled",
                        "jsonResponseFormatEnabled",
                        "jsonMaxCompletionTokens",
                        "jsonSkillContextRetryEnabled",
                        "jsonSkillContextRetryMaxChars",
                        "modelCallTimeoutSeconds",
                        "degradationMode"),
                List.copyOf(result.keySet()));
    }

    @Test
    void statusCreatesOrderedMapWhenBaseStatusIsNull() {
        Map<String, Object> result = policy.status(null, settings(true));

        assertTrue(result instanceof LinkedHashMap);
        assertEquals(List.of(
                        "failOnLlmDegradation",
                        "jsonRepairRetryEnabled",
                        "jsonResponseFormatEnabled",
                        "jsonMaxCompletionTokens",
                        "jsonSkillContextRetryEnabled",
                        "jsonSkillContextRetryMaxChars",
                        "modelCallTimeoutSeconds",
                        "degradationMode"),
                List.copyOf(result.keySet()));
    }

    @Test
    void failOpenRejectAndDegradeOnlyWarn() {
        LlmRuntimeSettings settings = settings(false);

        assertDoesNotThrow(() -> policy.reject(settings, "planner", "missing model"));
        assertDoesNotThrow(() -> policy.degrade(
                settings,
                "planner",
                "call failed",
                new IllegalStateException("boom")));
    }

    @Test
    void failClosedRejectUsesExceptionWithoutCauseAndPreservesNulls() {
        OpsLlmDegradationException error = assertThrows(
                OpsLlmDegradationException.class,
                () -> policy.reject(settings(true), null, null));

        assertEquals("null LLM 降级被禁止：null", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void failClosedDegradePreservesOriginalCauseAndNullValues() {
        IllegalArgumentException cause = new IllegalArgumentException("boom");
        OpsLlmDegradationException error = assertThrows(
                OpsLlmDegradationException.class,
                () -> policy.degrade(settings(true), "reviewer", null, cause));

        assertEquals("reviewer LLM 降级被禁止：null", error.getMessage());
        assertSame(cause, error.getCause());

        OpsLlmDegradationException nullError = assertThrows(
                OpsLlmDegradationException.class,
                () -> policy.degrade(settings(true), null, null, null));
        assertEquals("null LLM 降级被禁止：null", nullError.getMessage());
        assertNull(nullError.getCause());
    }

    private LlmRuntimeSettings settings(boolean failOnLlmDegradation) {
        return new LlmRuntimeSettings(
                failOnLlmDegradation,
                true,
                false,
                1_234,
                true,
                16_234,
                234);
    }
}
