package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMySqlSlowSqlSettingsTest {

    @Test
    void shouldBoundSampleSizeAndThreshold() {
        OpsMySqlSlowSqlSettings minimum = new OpsMySqlSlowSqlSettings(false, 0, -10D, false);
        OpsMySqlSlowSqlSettings maximum = new OpsMySqlSlowSqlSettings(true, 100, 123.5D, true);

        assertAll(
                () -> assertFalse(minimum.enabled()),
                () -> assertEquals(1, minimum.sampleSize()),
                () -> assertEquals(0D, minimum.thresholdMs()),
                () -> assertFalse(minimum.performanceSchemaFallback()),
                () -> assertTrue(maximum.enabled()),
                () -> assertEquals(50, maximum.sampleSize()),
                () -> assertEquals(123.5D, maximum.thresholdMs()),
                () -> assertTrue(maximum.performanceSchemaFallback()));
    }

    @Test
    void defaultsShouldRemainStable() {
        OpsMySqlSlowSqlSettings defaults = OpsMySqlSlowSqlSettings.defaults();

        assertAll(
                () -> assertTrue(defaults.enabled()),
                () -> assertEquals(10, defaults.sampleSize()),
                () -> assertEquals(500D, defaults.thresholdMs()),
                () -> assertTrue(defaults.performanceSchemaFallback()));
    }
}
