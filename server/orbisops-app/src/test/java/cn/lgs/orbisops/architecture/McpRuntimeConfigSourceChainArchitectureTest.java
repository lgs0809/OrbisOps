package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRuntimeConfigSourceChainArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void resolverMustDependOnOneOrderedSourceChainInsteadOfRepositories() throws IOException {
        String resolver = read(RUNTIME + "OpsRuntimeMcpResolver.java");
        String configuration = read(RUNTIME + "OpsRuntimeMcpResolverConfiguration.java");

        assertAll(
                () -> assertTrue(resolver.contains("OpsMcpRuntimeConfigSourceChain configSources")),
                () -> assertTrue(resolver.contains("configSources.resolve(")),
                () -> assertTrue(resolver.contains("MCP_CONFIG_RESOLVED")),
                () -> assertTrue(resolver.contains("fallbackReason")),
                () -> assertFalse(resolver.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(resolver.contains("OpsSourceRepositoryService")),
                () -> assertFalse(resolver.contains("OpsProjectMcpRuntimeConfigService")),
                () -> assertFalse(resolver.contains("OpsLegacyMcpConfigMapper")),
                () -> assertTrue(configuration.contains("List<OpsMcpRuntimeConfigSource> sources")),
                () -> assertTrue(configuration.contains("OpsMcpRuntimeConfigResolutionTelemetry telemetry")),
                () -> assertTrue(configuration.contains("new OpsMcpRuntimeConfigSourceChain(sources, telemetry)")),
                () -> assertTrue(configuration.contains("authorizationProvider::getIfAvailable")));
    }

    @Test
    void sourceChainMustBeFirstMatchAndBlockedMustStopFallback() throws IOException {
        String chain = read(RUNTIME + "OpsMcpRuntimeConfigSourceChain.java");
        String result = read(RUNTIME + "OpsMcpRuntimeConfigSourceResult.java");
        String resolution = read(RUNTIME + "OpsMcpRuntimeConfigResolution.java");

        assertAll(
                () -> assertTrue(chain.contains("sorted(Comparator.comparingInt")),
                () -> assertTrue(chain.contains("if (result.outcome() == OpsMcpRuntimeConfigSourceResult.Outcome.MATCH)")),
                () -> assertTrue(chain.contains("if (result.outcome() == OpsMcpRuntimeConfigSourceResult.Outcome.BLOCKED)")),
                () -> assertTrue(chain.contains("return observed(new OpsMcpRuntimeConfigResolution(")),
                () -> assertTrue(chain.contains("MCP_CONFIG_SOURCE_DUPLICATE")),
                () -> assertTrue(chain.contains("MCP_CONFIG_SOURCE_ORDER_DUPLICATE")),
                () -> assertFalse(chain.contains("putAll(")),
                () -> assertFalse(chain.contains(".merge(")),
                () -> assertTrue(result.contains("MATCH")),
                () -> assertTrue(result.contains("MISS")),
                () -> assertTrue(result.contains("UNAVAILABLE")),
                () -> assertTrue(result.contains("BLOCKED")),
                () -> assertTrue(resolution.contains("List.copyOf(attempts)")),
                () -> assertTrue(resolution.contains("fallbackReason()")));
    }

    @Test
    void sourceIdentityAndPriorityMustRemainStable() throws IOException {
        String project = read(RUNTIME + "OpsProjectMcpRuntimeConfigSource.java");
        String legacy = read(RUNTIME + "OpsLegacyMcpRuntimeConfigSource.java");
        String source = read(RUNTIME + "OpsSourceRepositoryMcpRuntimeConfigSource.java");

        assertAll(
                () -> assertTrue(project.contains("SOURCE_ID = \"PROJECT_RUNTIME\"")),
                () -> assertTrue(project.contains("ORDER = 100")),
                () -> assertTrue(project.contains("service.resolve(request.projectId(), request.mcpId())")),
                () -> assertTrue(legacy.contains("SOURCE_ID = \"LEGACY_MCP_CATALOG\"")),
                () -> assertTrue(legacy.contains("ORDER = 200")),
                () -> assertTrue(legacy.contains("PROJECT_AUTHORIZATION_UNAVAILABLE")),
                () -> assertTrue(legacy.contains("PROJECT_MCP_NOT_AUTHORIZED")),
                () -> assertTrue(legacy.contains("OpsMcpRuntimeConfigSourceResult.blocked")),
                () -> assertTrue(source.contains("SOURCE_ID = \"SOURCE_REPOSITORY\"")),
                () -> assertTrue(source.contains("ORDER = 300")),
                () -> assertTrue(source.contains("repository.resolveMcpServer(")));
    }

    @Test
    void legacyRuntimeUsageMustBeObservableBeforeRemoval() throws IOException {
        String telemetry = read(RUNTIME + "OpsMcpRuntimeConfigResolutionTelemetry.java");

        assertAll(
                () -> assertTrue(telemetry.contains("orbisops.mcp.runtime.config.resolution")),
                () -> assertTrue(telemetry.contains("tag(\"source\", source)")),
                () -> assertTrue(telemetry.contains("tag(\"outcome\", outcome)")),
                () -> assertTrue(telemetry.contains("tag(\"fallback\", fallback)")),
                () -> assertTrue(telemetry.contains("legacyFallbackCount()")),
                () -> assertTrue(telemetry.contains("OpsLegacyMcpRuntimeConfigSource.SOURCE_ID")));
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
