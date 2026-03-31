package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMcpTransportSecurityPolicyTest {

    private final OpsMcpTransportSecuritySettings prodLikeSettings =
            OpsMcpTransportSecuritySettings.fromRaw(
                    true,
                    "sse,streamable-http",
                    "",
                    "",
                    "MYSQL_PASSWORD",
                    "Authorization,X-API-Key",
                    60);

    @Test
    void allowsPlatformGeneratedNodeStdioWithoutOpeningGlobalStdio() {
        OpsMcpTransportSecurityPolicy policy = new OpsMcpTransportSecurityPolicy(prodLikeSettings);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("generated-mysql")
                .transport("stdio")
                .command("node")
                .toolCapabilities(Map.of("platformGenerated", "true"))
                .build();

        assertDoesNotThrow(() -> policy.assertTransportAllowed(config, "stdio"));
        assertDoesNotThrow(() -> policy.assertStdioAllowed(config, "node"));
    }

    @Test
    void keepsUserSuppliedStdioBlockedByProdTransportPolicy() {
        OpsMcpTransportSecurityPolicy policy = new OpsMcpTransportSecurityPolicy(prodLikeSettings);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("user-stdio")
                .transport("stdio")
                .command("node")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> policy.assertTransportAllowed(config, "stdio"));
    }

    @Test
    void platformGeneratedMarkerDoesNotAuthorizeArbitraryStdioCommand() {
        OpsMcpTransportSecurityPolicy policy = new OpsMcpTransportSecurityPolicy(prodLikeSettings);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("generated-invalid")
                .transport("stdio")
                .command("bash")
                .toolCapabilities(Map.of("platformGenerated", "true"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> policy.assertStdioAllowed(config, "bash"));
    }
}
