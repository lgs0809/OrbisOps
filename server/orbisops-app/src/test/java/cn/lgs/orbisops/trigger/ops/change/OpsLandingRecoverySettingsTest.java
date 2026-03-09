package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLandingRecoverySettingsTest {

    @Test
    void normalizesUnsafeBatchAndReconciliationTimeout() {
        OpsLandingRecoverySettings settings =
                new OpsLandingRecoverySettings(true, 0, 2);

        assertTrue(settings.enabled());
        assertEquals(20, settings.batchSize());
        assertEquals(120, settings.reconciliationTimeoutSeconds());
    }
}
