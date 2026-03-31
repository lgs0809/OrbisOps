package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMcpTransportAdmissionPolicyTest {

    private final OpsMcpTransportAdmissionPolicy policy =
            new OpsMcpTransportAdmissionPolicy();
    private final OpsMcpTransportSecuritySettings settings =
            OpsMcpTransportSecuritySettings.fromRaw(
                    true,
                    "stdio,sse,streamable-http",
                    "node,python3",
                    "localhost,127.0.0.1",
                    "api_token",
                    "Authorization,X-API-Key",
                    60);

    @Test
    void normalizesTransportTimeoutAndCommandBasename() {
        assertEquals("stdio", policy.normalizeTransport(null));
        assertEquals("streamable-http", policy.normalizeTransport("HTTP"));
        assertEquals(60, policy.normalizeTimeout(300, settings));
        assertEquals(1, policy.normalizeTimeout(0, settings));
        policy.assertStdioAllowed("test-mcp", "/usr/bin/node", settings);
    }

    @Test
    void failsClosedForUnknownTransportCommandUnsafeArgsAndRemoteAddress() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertTransportAllowed("test-mcp", "websocket", settings));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertStdioAllowed("test-mcp", "/usr/bin/bash", settings));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertArgsAllowed("test-mcp", List.of("safe", "bad\narg"), settings));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertRemoteAddressAllowed(
                        "test-mcp", "https://user:pass@localhost", settings));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertRemoteAddressAllowed(
                        "test-mcp", "https://example.com", settings));
    }
}
