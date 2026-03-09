package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsPrometheusSettingsTest {

    @Test
    void shouldNormalizeAndBoundSettings() {
        OpsPrometheusSettings settings = new OpsPrometheusSettings(
                "  http://localhost:9090///  ",
                "  demo-app  ",
                0);

        assertAll(
                () -> assertEquals("http://localhost:9090", settings.baseUrl()),
                () -> assertEquals("demo-app", settings.jobName()),
                () -> assertEquals(1, settings.timeoutSeconds()));
    }

    @Test
    void defaultsShouldRemainStable() {
        OpsPrometheusSettings defaults = OpsPrometheusSettings.defaults();

        assertAll(
                () -> assertEquals("http://127.0.0.1:9090", defaults.baseUrl()),
                () -> assertEquals("", defaults.jobName()),
                () -> assertEquals(5, defaults.timeoutSeconds()));
    }
}
