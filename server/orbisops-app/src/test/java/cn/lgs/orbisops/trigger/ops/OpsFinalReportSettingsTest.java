package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsFinalReportSettingsTest {

    @Test
    void normalizesUnsafeReportLimitAndPreservesLlmPolicy() {
        OpsFinalReportSettings settings = new OpsFinalReportSettings(false, 100);

        assertFalse(settings.llmEnabled());
        assertEquals(8_000, settings.maxChars());
        assertEquals(20_000, new OpsFinalReportSettings(true, 20_000).maxChars());
    }
}
