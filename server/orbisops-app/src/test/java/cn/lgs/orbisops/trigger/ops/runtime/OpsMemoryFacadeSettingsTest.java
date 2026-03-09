package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMemoryFacadeSettingsTest {

    @Test
    void normalizesUnsafeLimitsAndComputesCaptureBuffer() {
        OpsMemoryFacadeSettings settings = new OpsMemoryFacadeSettings(
                true, 0, 1, -1, 2_000, -1, 0L, true, true, Double.NaN);

        assertEquals(12, settings.hotMaxMessages());
        assertEquals(24, settings.hotBufferMessages());
        assertEquals(8, settings.itemMatchLimit());
        assertEquals(8, settings.semanticTopK());
        assertEquals(8_000, settings.contextMaxChars());
        assertEquals(1_200L, settings.assembleTimeoutMillis());
        assertEquals(24, settings.captureBufferSize());
        assertEquals(6D, settings.recencyHalfLifeTurns());
    }

    @Test
    void separatesConfiguredDefaultsFromLegacyConstructorBehavior() {
        assertTrue(OpsMemoryFacadeSettings.defaults().enabled());
        assertFalse(OpsMemoryFacadeSettings.legacyConstructorDefaults().enabled());
    }
}
