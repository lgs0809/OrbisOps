package cn.lgs.orbisops.trigger.application.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQualityEvalSettingsTest {

    @Test
    void springDefaultsAndLegacyConstructorDefaultsRemainDistinct() {
        RagQualityEvalSettings defaults = RagQualityEvalSettings.defaults();
        RagQualityEvalSettings legacy =
                RagQualityEvalSettings.legacyConstructorDefaults();

        assertTrue(defaults.rerankEnabled());
        assertEquals("cohere", defaults.rerankProvider());
        assertEquals("http://127.0.0.1:8110", defaults.rerankBaseUrl());
        assertEquals("local-rag-models", defaults.rerankApiKey());
        assertEquals("v1/rerank", defaults.rerankPath());
        assertEquals("Qwen/Qwen3-VL-Reranker-2B", defaults.rerankModel());
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
