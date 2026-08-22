package cn.lgs.orbisops.trigger.application.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RagQualityEvalSettingsTest {

    @Test
    void defaultsRemainProviderNeutralUntilRerankIsConfigured() {
        RagQualityEvalSettings defaults = RagQualityEvalSettings.defaults();
        RagQualityEvalSettings legacy =
                RagQualityEvalSettings.legacyConstructorDefaults();

        assertFalse(defaults.rerankEnabled());
        assertEquals("", defaults.rerankProvider());
        assertEquals("", defaults.rerankBaseUrl());
        assertEquals("", defaults.rerankApiKey());
        assertEquals("v1/rerank", defaults.rerankPath());
        assertEquals("", defaults.rerankModel());
        assertFalse(legacy.rerankEnabled());
        assertEquals("", legacy.rerankProvider());
        assertEquals("", legacy.rerankModel());
    }

    @Test
    void trimsNullableStringSettingsWithoutInventingDefaults() {
        RagQualityEvalSettings settings = new RagQualityEvalSettings(
                true,
                " cohere ",
                null,
                " key ",
                " path ",
                " model ");

        assertEquals("cohere", settings.rerankProvider());
        assertEquals("", settings.rerankBaseUrl());
        assertEquals("key", settings.rerankApiKey());
        assertEquals("path", settings.rerankPath());
        assertEquals("model", settings.rerankModel());
    }
}
