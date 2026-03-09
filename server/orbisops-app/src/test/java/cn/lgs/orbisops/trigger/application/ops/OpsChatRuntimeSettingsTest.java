package cn.lgs.orbisops.trigger.application.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsChatRuntimeSettingsTest {

    @Test
    void shouldBoundTimeoutAndKeepZeroAsDisabled() {
        OpsChatRuntimeSettings disabled = new OpsChatRuntimeSettings(-1L);
        OpsChatRuntimeSettings maximum = new OpsChatRuntimeSettings(5000L);

        assertAll(
                () -> assertEquals(0L, disabled.syncTimeoutSeconds()),
                () -> assertEquals(3600L, maximum.syncTimeoutSeconds()));
    }

    @Test
    void defaultsShouldRemainStable() {
        assertEquals(90L, OpsChatRuntimeSettings.defaults().syncTimeoutSeconds());
    }
}
