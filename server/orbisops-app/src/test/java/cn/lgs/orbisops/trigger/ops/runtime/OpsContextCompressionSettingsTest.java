package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsContextCompressionSettingsTest {

    @Test
    void normalizesNumericLimitsAndSeparatesSpringFromLegacyDefaults() {
        OpsContextCompressionSettings normalized =
                new OpsContextCompressionSettings(true, 0, -1, true, 300_000);

        assertEquals(20, normalized.thresholdMessages());
        assertEquals(8, normalized.keepRecent());
        assertEquals(6_000, normalized.modelMaxInputChars());
        assertTrue(OpsContextCompressionSettings.defaults().enabled());
        assertFalse(OpsContextCompressionSettings.legacyConstructorDefaults().enabled());
    }
}
