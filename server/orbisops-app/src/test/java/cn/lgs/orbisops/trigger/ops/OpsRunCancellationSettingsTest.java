package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsRunCancellationSettingsTest {

    @Test
    void boundsRetentionAndPreservesLegacyMinimum() {
        assertEquals(600L, new OpsRunCancellationSettings(1L).retentionSeconds());
        assertEquals(60L, OpsRunCancellationSettings.legacyConstructorDefaults().retentionSeconds());
        assertEquals(600_000L, OpsRunCancellationSettings.defaults().retentionMillis());
    }
}
