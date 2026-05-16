package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsEsLogSettingsTest {

    @Test
    void shouldNormalizeAndBoundSettings() {
        OpsEsLogSettings settings = new OpsEsLogSettings(
                "  http://localhost:9200///  ",
                "  logs-*  ",
                0,
                200);

        assertAll(
                () -> assertEquals("http://localhost:9200", settings.baseUrl()),
                () -> assertEquals("logs-*", settings.index()),
                () -> assertEquals(1, settings.timeoutSeconds()),
                () -> assertEquals(100, settings.sampleSize()),
                () -> assertEquals("http://localhost:9200/logs-*", settings.endpoint()));
    }

    @Test
    void defaultsAndEmptyIndexShouldRemainUsable() {
        OpsEsLogSettings defaults = OpsEsLogSettings.defaults();
        OpsEsLogSettings emptyIndex = new OpsEsLogSettings("http://localhost:9200/", null, 5, -1);

        assertAll(
                () -> assertEquals("http://127.0.0.1:9200", defaults.baseUrl()),
                () -> assertEquals("", defaults.index()),
                () -> assertEquals(5, defaults.timeoutSeconds()),
                () -> assertEquals(8, defaults.sampleSize()),
                () -> assertEquals("http://localhost:9200", emptyIndex.endpoint()),
                () -> assertEquals(1, emptyIndex.sampleSize()));
    }
}
