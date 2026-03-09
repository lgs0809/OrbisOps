package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsAlertTriggerSettingsTest {

    @Test
    void normalizesUnsafeValuesAndKeepsMaxWaitNotBelowDebounce() {
        OpsAlertTriggerSettings settings = new OpsAlertTriggerSettings(
                1,
                0,
                1,
                600,
                300,
                0,
                101);

        assertEquals(300, settings.signatureMaxSkewSeconds());
        assertEquals(8, settings.outboxMaxAttempts());
        assertEquals(120, settings.outboxLockTimeoutSeconds());
        assertEquals(600, settings.aggregationDebounceSeconds());
        assertEquals(900, settings.aggregationMaxWaitSeconds());
        assertEquals(100, settings.projectMaxQueued());
        assertEquals(4, settings.projectMaxRunning());
    }

    @Test
    void preservesValuesInsideSupportedRanges() {
        OpsAlertTriggerSettings settings = new OpsAlertTriggerSettings(
                600,
                12,
                300,
                60,
                1_200,
                500,
                8);

        assertEquals(600, settings.signatureMaxSkewSeconds());
        assertEquals(12, settings.outboxMaxAttempts());
        assertEquals(300, settings.outboxLockTimeoutSeconds());
        assertEquals(60, settings.aggregationDebounceSeconds());
        assertEquals(1_200, settings.aggregationMaxWaitSeconds());
        assertEquals(500, settings.projectMaxQueued());
        assertEquals(8, settings.projectMaxRunning());
    }
}
