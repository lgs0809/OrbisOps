package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMcpTransportSecurityPolicyTest {

    private OpsMcpTransportSecurityPolicy policy;
    private OpsMcpServerConfig config;

    @BeforeEach
    void setUp() {
        policy = new OpsMcpTransportSecurityPolicy(
                OpsMcpTransportSecuritySettings.fromRaw(
                        true,
                        "stdio,sse,streamable-http",
                        "node,python3",
                        "localhost,127.0.0.1",
                        "api_token",
                        "Authorization,X-API-Key",
                        60));
        config = OpsMcpServerConfig.builder().name("test-mcp").build();
    }

    @Test
    void transportAliasesAndTimeoutMustBeNormalized() {
        assertEquals("stdio", policy.normalizeTransport(null));
        assertEquals("streamable-http", policy.normalizeTransport("HTTP"));
        assertEquals(60, policy.normalizeTimeout(300));
        assertEquals(1, policy.normalizeTimeout(0));
    }

    @Test
    void unlistedTransportAndCommandMustFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> policy.assertTransportAllowed(config, "websocket"));
        assertThrows(IllegalArgumentException.class, () -> policy.assertStdioAllowed(config, "/usr/bin/bash"));
    }

    @Test
    void remoteAddressMustRejectUserInfoAndUnknownHost() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertRemoteAddressAllowed(config, "https://user:pass@localhost"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertRemoteAddressAllowed(config, "https://example.com"));
    }

    @Test
    void sensitiveEnvironmentAndUnknownHeadersMustFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.safeEnv(config, Map.of("DB_PASSWORD", "secret")));
        assertThrows(IllegalArgumentException.class,
                () -> policy.safeHeaders(config, Map.of("Cookie", "value")));
        assertEquals(Map.of("API_TOKEN", "secret"), policy.safeEnv(config, Map.of("API_TOKEN", "secret")));
    }

    @Test
    void unsafeArgumentsMustFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertArgsAllowed(config, List.of("safe", "bad\narg")));
    }
}
