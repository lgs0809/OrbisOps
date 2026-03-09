package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsInvestigationExecutorSettingsTest {

    @Test
    void shouldBoundExecutionLimitsWithoutChangingFeatureFlags() {
        OpsInvestigationExecutorSettings minimum = new OpsInvestigationExecutorSettings(
                false,
                false,
                0,
                -1,
                0);
        OpsInvestigationExecutorSettings maximum = new OpsInvestigationExecutorSettings(
                true,
                true,
                1000,
                100,
                1000);

        assertAll(
                () -> assertFalse(minimum.mainReflectionLlmEnabled()),
                () -> assertFalse(minimum.parallelExecutionEnabled()),
                () -> assertEquals(1, minimum.maxTaskExecutions()),
                () -> assertEquals(0, minimum.maxAdjustmentsLimit()),
                () -> assertEquals(1, minimum.defaultMaxEvidenceItems()),
                () -> assertTrue(maximum.mainReflectionLlmEnabled()),
                () -> assertTrue(maximum.parallelExecutionEnabled()),
                () -> assertEquals(100, maximum.maxTaskExecutions()),
                () -> assertEquals(20, maximum.maxAdjustmentsLimit()),
                () -> assertEquals(100, maximum.defaultMaxEvidenceItems()));
    }

    @Test
    void defaultsShouldRemainStable() {
        OpsInvestigationExecutorSettings defaults = OpsInvestigationExecutorSettings.defaults();

        assertAll(
                () -> assertTrue(defaults.mainReflectionLlmEnabled()),
                () -> assertTrue(defaults.parallelExecutionEnabled()),
                () -> assertEquals(8, defaults.maxTaskExecutions()),
                () -> assertEquals(3, defaults.maxAdjustmentsLimit()),
                () -> assertEquals(12, defaults.defaultMaxEvidenceItems()));
    }
}
