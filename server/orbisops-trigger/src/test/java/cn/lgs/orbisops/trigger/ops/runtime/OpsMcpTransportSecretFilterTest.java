package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMcpTransportSecretFilterTest {

    private final OpsMcpTransportSecretFilter filter = new OpsMcpTransportSecretFilter();
    private final OpsMcpTransportSecuritySettings settings =
            OpsMcpTransportSecuritySettings.fromRaw(
                    true,
                    "stdio,sse,streamable-http",
                    "node",
                    "localhost",
                    "api_token",
                    "Authorization,X-API-Key",
                    60);

    @Test
    void allowsExplicitSensitiveEnvAndWhitelistedHeaders() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("API_TOKEN", null);
        Map<String, String> headers = Map.of("Authorization", "Bearer x");

        assertEquals(env, filter.safeEnv("test-mcp", env, settings));
        assertEquals(headers, filter.safeHeaders("test-mcp", headers, settings));
    }

    @Test
    void rejectsUnlistedSensitiveEnvAndHeaders() {
        assertThrows(IllegalArgumentException.class,
                () -> filter.safeEnv("test-mcp", Map.of("DB_PASSWORD", "secret"), settings));
        assertThrows(IllegalArgumentException.class,
                () -> filter.safeHeaders("test-mcp", Map.of("Cookie", "value"), settings));
    }

    @Test
    void disabledSecurityPreservesOriginalMapIdentity() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("DB_PASSWORD", "secret");

        assertSame(source, filter.safeEnv(
                "test-mcp",
                source,
                OpsMcpTransportSecuritySettings.legacyConstructorDefaults()));
    }
}
