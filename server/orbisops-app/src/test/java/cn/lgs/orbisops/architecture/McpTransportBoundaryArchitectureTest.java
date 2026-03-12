package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTransportBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void clientFactoryMustOwnSdkTransportConstructionAndSecretResolution() throws IOException {
        String factory = read(RUNTIME + "OpsMcpClientFactory.java");
        assertAll(
                () -> assertTrue(factory.contains("class OpsMcpClientFactory")),
                () -> assertTrue(factory.contains("McpSyncClient create(OpsMcpServerConfig config)")),
                () -> assertTrue(factory.contains("HttpClientSseClientTransport")),
                () -> assertTrue(factory.contains("WebClientStreamableHttpTransport")),
                () -> assertTrue(factory.contains("StdioClientTransport")),
                () -> assertTrue(factory.contains("JacksonMcpJsonMapper")),
                () -> assertTrue(factory.contains("new JacksonMcpJsonMapper(new ObjectMapper())")),
                () -> assertTrue(factory.contains("JacksonJsonSchemaValidatorSupplier")),
                () -> assertTrue(factory.contains("new JacksonJsonSchemaValidatorSupplier().get()")),
                () -> assertTrue(factory.contains(".jsonSchemaValidator(jsonSchemaValidator)")),
                () -> assertFalse(factory.contains("McpJsonMapper.getDefault()")),
                () -> assertFalse(factory.contains("JsonSchemaValidatorSupplier.getDefault()")),
                () -> assertTrue(factory.contains("secretResolver.resolve")),
                () -> assertTrue(factory.contains("securityPolicy.assertTransportAllowed")));
    }

    @Test
    void securityFacadeMustDelegateTypedAllowlistsAdmissionAndSecretFiltering() throws IOException {
        String facade = read(RUNTIME + "OpsMcpTransportSecurityPolicy.java");
        String settings = read(RUNTIME + "OpsMcpTransportSecuritySettings.java");
        String admission = read(RUNTIME + "OpsMcpTransportAdmissionPolicy.java");
        String secretFilter = read(RUNTIME + "OpsMcpTransportSecretFilter.java");
        String configuration = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/ops/"
                + "OpsMcpTransportSecurityConfiguration.java");
        assertAll(
                () -> assertTrue(facade.contains("OpsMcpTransportSecuritySettings settings")),
                () -> assertTrue(facade.contains("OpsMcpTransportAdmissionPolicy admissionPolicy")),
                () -> assertTrue(facade.contains("OpsMcpTransportSecretFilter secretFilter")),
                () -> assertTrue(facade.contains("legacyConstructorDefaults()")),
                () -> assertTrue(facade.contains("assertStdioAllowed")),
                () -> assertTrue(facade.contains("assertArgsAllowed")),
                () -> assertTrue(facade.contains("assertRemoteAddressAllowed")),
                () -> assertTrue(facade.contains("safeEnv")),
                () -> assertTrue(facade.contains("safeHeaders")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("URI.create")),
                () -> assertFalse(facade.contains("SENSITIVE_KEY_PATTERN")),
                () -> assertTrue(facade.lines().count() <= 90),
                () -> assertTrue(settings.contains("public record OpsMcpTransportSecuritySettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(admission.contains("URI.create(baseUri)")),
                () -> assertTrue(admission.contains("UNSAFE_ARG_PATTERN")),
                () -> assertTrue(admission.contains("commandName(")),
                () -> assertFalse(admission.contains("@Service")),
                () -> assertTrue(secretFilter.contains("SENSITIVE_KEY_PATTERN")),
                () -> assertTrue(secretFilter.contains("Map<String, String> safeEnv(")),
                () -> assertTrue(secretFilter.contains("Map<String, String> safeHeaders(")),
                () -> assertFalse(secretFilter.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.mcp.security.allowed-transports")),
                () -> assertTrue(configuration.contains("orbisops.mcp.security.allowed-env-keys")),
                () -> assertTrue(configuration.contains("orbisops.mcp.security.max-timeout-seconds")));
    }

    @Test
    void remoteClientAdapterMustOwnFactoryUseAndProviderMustNotReclaimTransportBoundary() throws IOException {
        String adapter = read(RUNTIME + "OpsMcpRemoteClientAdapter.java");
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");
        assertAll(
                () -> assertTrue(adapter.contains("private final OpsMcpClientFactory clientFactory;")),
                () -> assertTrue(adapter.contains("clientFactory.cacheKey(config)")),
                () -> assertTrue(adapter.contains("clientFactory.create(config)")),
                () -> assertFalse(provider.contains("OpsMcpClientFactory")),
                () -> assertFalse(provider.contains("clientFactory.cacheKey")),
                () -> assertFalse(provider.contains("clientFactory.create")),
                () -> assertFalse(provider.contains("HttpClientSseClientTransport")),
                () -> assertFalse(provider.contains("WebClientStreamableHttpTransport")),
                () -> assertFalse(provider.contains("StdioClientTransport")),
                () -> assertFalse(provider.contains("orbisops.mcp.security.allowed-transports")),
                () -> assertFalse(provider.contains("OpsSecretResolver secretResolver")),
                () -> assertFalse(provider.contains("private McpSyncClient createClient")),
                () -> assertFalse(provider.contains("private void assertTransportAllowed")),
                () -> assertFalse(provider.contains("private Map<String, String> safeEnv")),
                () -> assertFalse(provider.contains("record SseAddress")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
