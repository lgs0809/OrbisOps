package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWorkSessionRecoverySettingsTest {

    @Test
    void boundsBatchSizeAndKeepsRecoveryEnabledByDefault() {
        OpsWorkSessionRecoverySettings settings =
                new OpsWorkSessionRecoverySettings(true, 0);

        assertTrue(settings.enabled());
        assertEquals(100, settings.batchSize());
        assertEquals(48, new OpsWorkSessionRecoverySettings(false, 48).batchSize());
    }
}
