package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMcpTransportSecretFilterTest {

    private final OpsMcpTransportSecretFilter filter = new OpsMcpTransportSecretFilter();

    @Test
    void nonSecretControlKeysContainingPluralKeysAreAllowed() {
        Map<String, String> result = filter.safeEnv(
                "redis-mcp",
                Map.of(
                        "REDIS_MCP_MAX_KEYS", "100",
                        "REDIS_TIMEOUT_MS", "3000",
                        "REDIS_PORT", "6379"),
                OpsMcpTransportSecuritySettings.defaults());

        assertEquals("100", result.get("REDIS_MCP_MAX_KEYS"));
        assertEquals("3000", result.get("REDIS_TIMEOUT_MS"));
    }

    @Test
    void actualSecretKeysStillRequireExplicitAllowlist() {
        assertThrows(IllegalArgumentException.class, () -> filter.safeEnv(
                "redis-mcp",
                Map.of("REDIS_PASSWORD", "placeholder"),
                OpsMcpTransportSecuritySettings.defaults()));
        assertThrows(IllegalArgumentException.class, () -> filter.safeEnv(
                "api-mcp",
                Map.of("API_KEY", "placeholder"),
                OpsMcpTransportSecuritySettings.defaults()));
    }
}
