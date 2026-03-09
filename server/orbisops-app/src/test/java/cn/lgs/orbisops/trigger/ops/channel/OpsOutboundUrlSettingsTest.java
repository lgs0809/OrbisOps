package cn.lgs.orbisops.trigger.ops.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsOutboundUrlSettingsTest {

    @Test
    void normalizesAndDeduplicatesHostPatterns() {
        OpsOutboundUrlSettings settings = OpsOutboundUrlSettings.fromRaw(
                true,
                " Hooks.Example.com,*.example.net,hooks.example.com ");

        assertTrue(settings.allowLoopback());
        assertEquals(2, settings.allowedHosts().size());
        assertTrue(settings.allowedHosts().contains("hooks.example.com"));
        assertTrue(settings.allowedHosts().contains("*.example.net"));
    }

    @Test
    void defaultsRemainLoopbackDeniedAndHostUnrestricted() {
        OpsOutboundUrlSettings settings = OpsOutboundUrlSettings.defaults();

        assertFalse(settings.allowLoopback());
        assertTrue(settings.allowedHosts().isEmpty());
    }
}
