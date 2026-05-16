package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMemoryExtractionSettingsTest {

    @Test
    void normalizesLimitsAndSeparatesConfiguredFromLegacyDefaults() {
        OpsMemoryExtractionSettings settings =
                new OpsMemoryExtractionSettings(true, -1, true, 300_000);

        assertEquals(6, settings.maxItemsPerMessage());
        assertEquals(4_000, settings.modelMaxInputChars());
        assertTrue(OpsMemoryExtractionSettings.defaults().enabled());
        assertFalse(OpsMemoryExtractionSettings.legacyConstructorDefaults().enabled());
    }
}
