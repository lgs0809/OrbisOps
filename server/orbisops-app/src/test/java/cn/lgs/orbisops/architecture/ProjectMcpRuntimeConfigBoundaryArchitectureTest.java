package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMcpRuntimeConfigBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void factoryMustKeepCredentialHandlingAndDelegateRuntimeProjection()
            throws IOException {
        String factory = read(RUNTIME + "OpsProjectMcpRuntimeConfigFactory.java");

        assertAll(
                () -> assertTrue(factory.contains(
                        "private final OpsSecretResolver secretResolver;")),
                () -> assertTrue(factory.contains(
                        "private final OpsProjectMcpConfigBuilder configBuilder;")),
                () -> assertTrue(factory.contains(
                        "configBuilder.buildInternal(")),
                () -> assertTrue(factory.contains(
                        "configBuilder.buildExternal(")),
                () -> assertTrue(factory.contains("resolvedCredential(")),
                () -> assertTrue(factory.contains(
                        "MCP_CREDENTIAL_REFERENCE_INVALID")),
                () -> assertFalse(factory.contains("OpsMcpServerConfig.builder(")),
                () -> assertFalse(factory.contains("MYSQL_HOST")),
                () -> assertFalse(factory.contains("POSTGRES_HOST")),
                () -> assertFalse(factory.contains("REDIS_HOST")),
                () -> assertFalse(factory.contains("RABBITMQ_MANAGEMENT_URL")),
                () -> assertFalse(factory.contains("ES_HOST")),
                () -> assertFalse(factory.contains("PROMETHEUS_URL")),
                () -> assertFalse(factory.contains("scriptLocator.locate(")),
                () -> assertFalse(factory.contains("JSON.toJSONString")),
                () -> assertFalse(factory.contains("URI")),
                () -> assertTrue(factory.contains("ProjectMcpDefinition")),
                () -> assertTrue(factory.contains("ProjectResourceDefinition")),
                () -> assertTrue(factory.contains("mcpView(")),
                () -> assertTrue(factory.contains("resourceView(")),
                () -> assertTrue(factory.lines().count() < 170));
    }

    @Test
    void configBuilderMustOwnRuntimeDtoAndScriptProjection()
            throws IOException {
        String builder = read(RUNTIME + "OpsProjectMcpConfigBuilder.java");

        assertAll(
                () -> assertTrue(builder.contains("OpsMcpServerConfig.builder(")),
                () -> assertTrue(builder.contains("scriptLocator.locate(")),
                () -> assertTrue(builder.contains(
                        "environmentProjector.project(")),
                () -> assertTrue(builder.contains("buildInternal(")),
                () -> assertTrue(builder.contains("buildExternal(")),
                () -> assertTrue(builder.contains("MYSQL_MCP_SCRIPT")),
                () -> assertTrue(builder.contains("POSTGRES_MCP_SCRIPT")),
                () -> assertTrue(builder.contains("REDIS_MCP_SCRIPT")),
                () -> assertFalse(builder.contains("OpsSecretResolver")),
                () -> assertFalse(builder.contains("passwordRef")),
                () -> assertFalse(builder.contains("apiKeyRef")),
                () -> assertFalse(builder.contains("secretResolver")),
                () -> assertTrue(builder.contains("resourceEnvironment")),
                () -> assertTrue(builder.contains("permissionProfile")),
                () -> assertTrue(builder.contains("allowedStages")),
                () -> assertTrue(builder.contains("effectCeiling")),
                () -> assertTrue(builder.contains("terminalPolicyRef")),
                () -> assertTrue(builder.lines().count() < 280));
    }

    @Test
    void environmentProjectorMustOwnResourceSpecificEnvironmentKeys()
            throws IOException {
        String projector = read(
                RUNTIME + "OpsProjectMcpEnvironmentProjector.java");

        assertAll(
                () -> assertTrue(projector.contains("MYSQL_HOST")),
                () -> assertTrue(projector.contains("POSTGRES_HOST")),
                () -> assertTrue(projector.contains("REDIS_HOST")),
                () -> assertTrue(projector.contains("RABBITMQ_MANAGEMENT_URL")),
                () -> assertTrue(projector.contains("ES_HOST")),
                () -> assertTrue(projector.contains("PROMETHEUS_URL")),
                () -> assertTrue(projector.contains(
                        "endpointParser.resourceUri(")),
                () -> assertTrue(projector.contains("JSON.toJSONString")),
                () -> assertFalse(projector.contains("OpsSecretResolver")),
                () -> assertFalse(projector.contains("OpsMcpServerConfig")),
                () -> assertFalse(projector.contains("scriptLocator")),
                () -> assertFalse(projector.contains("passwordRef")),
                () -> assertFalse(projector.contains("apiKeyRef")),
                () -> assertTrue(projector.lines().count() < 230));
    }

    @Test
    void typePolicyMustOwnCapabilitiesTimeoutDefaultsAndPermissions() throws IOException {
        String policy = read(RUNTIME + "OpsProjectMcpTypePolicy.java");

        assertAll(
                () -> assertTrue(policy.contains("allowedTools(")),
                () -> assertTrue(policy.contains("timeoutSeconds(")),
                () -> assertTrue(policy.contains("defaultEndpoint(")),
                () -> assertTrue(policy.contains("defaultUsername(")),
                () -> assertTrue(policy.contains("multiObjectPermission(")),
                () -> assertFalse(policy.contains("OpsSecretResolver")),
                () -> assertFalse(policy.contains("Files.exists")),
                () -> assertFalse(policy.contains("OpsMcpServerConfig.builder(")),
                () -> assertTrue(policy.lines().count() < 170));
    }

    @Test
    void endpointParserMustOwnOnlyEndpointAndDatabaseProjection() throws IOException {
        String parser = read(RUNTIME + "OpsProjectMcpEndpointParser.java");

        assertAll(
                () -> assertTrue(parser.contains("resourceUri(")),
                () -> assertTrue(parser.contains("databaseName(")),
                () -> assertTrue(parser.contains("jdbc:")),
                () -> assertFalse(parser.contains("OpsSecretResolver")),
                () -> assertFalse(parser.contains("OpsMcpServerConfig")),
                () -> assertFalse(parser.contains("Files.exists")),
                () -> assertTrue(parser.lines().count() < 50));
    }

    @Test
    void scriptLocatorMustOwnOnlyFilesystemLookup() throws IOException {
        String locator = read(RUNTIME + "OpsProjectMcpScriptLocator.java");

        assertAll(
                () -> assertTrue(locator.contains("Files::exists")),
                () -> assertTrue(locator.contains("scripts")),
                () -> assertTrue(locator.contains("mcp")),
                () -> assertFalse(locator.contains("OpsSecretResolver")),
                () -> assertFalse(locator.contains("OpsMcpServerConfig")),
                () -> assertFalse(locator.contains("URI.create")),
                () -> assertTrue(locator.lines().count() < 45));
    }

    @Test
    void configValuesMustOwnUntypedMapExtractionOnly() throws IOException {
        String values = read(RUNTIME + "OpsProjectMcpConfigValues.java");

        assertAll(
                () -> assertTrue(values.contains("intValue(")),
                () -> assertTrue(values.contains("stringList(")),
                () -> assertTrue(values.contains("map(")),
                () -> assertTrue(values.contains("text(")),
                () -> assertFalse(values.contains("OpsSecretResolver")),
                () -> assertFalse(values.contains("OpsMcpServerConfig")),
                () -> assertFalse(values.contains("URI")),
                () -> assertTrue(values.lines().count() < 75));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
