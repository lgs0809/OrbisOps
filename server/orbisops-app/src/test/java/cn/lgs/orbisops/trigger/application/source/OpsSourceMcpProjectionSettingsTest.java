package cn.lgs.orbisops.trigger.application.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsSourceMcpProjectionSettingsTest {

    @Test
    void shouldBoundRequestTimeout() {
        assertAll(
                () -> assertEquals(1, new OpsSourceMcpProjectionSettings(0).requestTimeoutSeconds()),
                () -> assertEquals(300, new OpsSourceMcpProjectionSettings(1000).requestTimeoutSeconds()),
                () -> assertEquals(8, OpsSourceMcpProjectionSettings.defaults().requestTimeoutSeconds()));
    }
}
