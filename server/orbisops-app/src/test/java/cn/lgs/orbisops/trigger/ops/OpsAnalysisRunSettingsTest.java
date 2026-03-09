package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisRunSettingsTest {

    @Test
    void shouldBoundMemoryRetentionAndPreserveAdmissionFlags() {
        OpsAnalysisRunSettings minimum = new OpsAnalysisRunSettings(0, true, false);
        OpsAnalysisRunSettings maximum = new OpsAnalysisRunSettings(20_000, false, true);

        assertAll(
                () -> assertEquals(1, minimum.maxMemoryRecords()),
                () -> assertTrue(minimum.allowInMemoryFallback()),
                () -> assertFalse(minimum.rejectWhenQueueFull()),
                () -> assertEquals(10_000, maximum.maxMemoryRecords()),
                () -> assertFalse(maximum.allowInMemoryFallback()),
                () -> assertTrue(maximum.rejectWhenQueueFull()));
    }

    @Test
    void defaultsShouldRemainFailClosedAndQueueBounded() {
        OpsAnalysisRunSettings defaults = OpsAnalysisRunSettings.defaults();

        assertAll(
                () -> assertEquals(200, defaults.maxMemoryRecords()),
                () -> assertFalse(defaults.allowInMemoryFallback()),
                () -> assertTrue(defaults.rejectWhenQueueFull()));
    }
}
