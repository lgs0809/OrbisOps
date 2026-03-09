package cn.lgs.orbisops.trigger.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAlertOutboxJobSettingsTest {

    @Test
    void normalizesBatchSizeAndSeparatesSpringFromLegacyDefaults() {
        assertEquals(20, new OpsAlertOutboxJobSettings(true, 0).batchSize());
        assertEquals(64, new OpsAlertOutboxJobSettings(true, 64).batchSize());
        assertTrue(OpsAlertOutboxJobSettings.defaults().enabled());
        assertFalse(OpsAlertOutboxJobSettings.legacyConstructorDefaults().enabled());
    }
}
