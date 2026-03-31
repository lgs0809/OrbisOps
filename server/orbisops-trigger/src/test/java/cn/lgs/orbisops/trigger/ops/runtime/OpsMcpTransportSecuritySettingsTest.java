package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMcpTransportSecuritySettingsTest {

    @Test
    void normalizesAliasesAllowlistsAndTimeout() {
        OpsMcpTransportSecuritySettings settings =
                OpsMcpTransportSecuritySettings.fromRaw(
                        true,
                        "stdio,HTTP,streamable_http",
                        " Node,node,Python3 ",
                        "LOCALHOST,127.0.0.1",
                        "API_TOKEN,api_token",
                        "Authorization,X-API-Key",
                        9_999);

        assertTrue(settings.enabled());
        assertTrue(settings.allowedTransports().contains("streamable-http"));
        assertEquals(2, settings.allowedStdioCommands().size());
        assertEquals(2, settings.allowedRemoteHosts().size());
        assertEquals(1, settings.allowedEnvKeys().size());
        assertEquals(60, settings.maxTimeoutSeconds());
    }

    @Test
    void separatesSpringDefaultsFromLegacyNoArgBehavior() {
        assertTrue(OpsMcpTransportSecuritySettings.defaults().enabled());
        OpsMcpTransportSecuritySettings legacy =
                OpsMcpTransportSecuritySettings.legacyConstructorDefaults();
        assertFalse(legacy.enabled());
        assertEquals(1, legacy.maxTimeoutSeconds());
    }
}
