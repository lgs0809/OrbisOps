package cn.lgs.orbisops.trigger.ops.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualAnalysisSettingsTest {

    @Test
    void defaultsMustRemainProviderNeutralUntilExplicitlyConfigured() {
        RagVisualAnalysisSettings settings = RagVisualAnalysisSettings.defaults();

        assertFalse(settings.enabled());
        assertTrue(settings.highValueOnly());
        assertEquals("openai", settings.provider());
        assertEquals("v1/chat/completions", settings.path());
        assertEquals("", settings.model());
        assertEquals("low", settings.detail());
        assertEquals(30, settings.timeoutSeconds());
        assertEquals(1200, settings.maxCompletionTokens());
        assertEquals("max_completion_tokens", settings.tokenLimitField());
        assertEquals("json_schema", settings.responseFormatMode());
        assertEquals(1, settings.maxRetries());
        assertEquals(3, settings.maxImagesPerDocument());
        assertEquals(4_194_304L, settings.maxImageBytes());
        assertEquals(144, settings.pdfRenderDpi());
    }

    @Test
    void valuesMustNormalizeEndpointAndApplyStableClamps() {
        RagVisualAnalysisSettings settings = new RagVisualAnalysisSettings(
                true, false, " OPENAI ", "https://api.example.com///", " key ",
                "///chat", " model ", " ", 1, 20, " ", " JSON_OBJECT ",
                -1, 0, 100, 400);

        assertTrue(settings.providerSupported());
        assertEquals("https://api.example.com/chat", settings.endpoint());
        assertEquals("key", settings.apiKey());
        assertEquals("model", settings.model());
        assertEquals("low", settings.detail());
        assertEquals(5, settings.timeoutSeconds());
        assertEquals(300, settings.maxCompletionTokens());
        assertEquals("max_completion_tokens", settings.tokenLimitField());
        assertEquals("json_object", settings.responseFormatMode());
        assertEquals(0, settings.maxRetries());
        assertEquals(1, settings.maxImagesPerDocument());
        assertEquals(200, settings.pdfRenderDpi());
    }
}
