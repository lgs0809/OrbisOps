package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsSemanticMemoryRetrievalSettingsTest {

    @Test
    void derivesNarrowPolicyFromSharedMemorySettings() {
        OpsMemoryFacadeSettings shared = new OpsMemoryFacadeSettings(
                true, 12, 24, 8, 21, 8_000, 1_200L, true, false, 9D);

        OpsSemanticMemoryRetrievalSettings settings =
                OpsSemanticMemoryRetrievalSettings.from(shared);

        assertEquals(21, settings.semanticTopK());
        assertFalse(settings.recencyAwareEnabled());
        assertEquals(9D, settings.recencyHalfLifeTurns());
    }

    @Test
    void normalizesUnsafeValues() {
        OpsSemanticMemoryRetrievalSettings settings =
                new OpsSemanticMemoryRetrievalSettings(2_000, true, Double.NaN);

        assertEquals(8, settings.semanticTopK());
        assertEquals(6D, settings.recencyHalfLifeTurns());
    }
}
