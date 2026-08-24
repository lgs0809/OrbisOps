package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRemoteClientInvocationArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void remoteClientAdapterMustOwnSessionCatalogAndSchemaProjection() throws IOException {
        String adapter = read(RUNTIME + "OpsMcpRemoteClientAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains("private final OpsMcpClientRegistry clientRegistry;")),
                () -> assertTrue(adapter.contains("private final OpsMcpClientFactory clientFactory;")),
                () -> assertTrue(adapter.contains("record Session")),
                () -> assertTrue(adapter.contains("Session open(OpsMcpServerConfig config)")),
                () -> assertTrue(adapter.contains("ToolCallback find(")),
                () -> assertTrue(adapter.contains("inspectDefinition")),
                () -> assertTrue(adapter.contains("inspectDefinitions")),
                () -> assertTrue(adapter.contains("clientRegistry.invalidateAll()")),
                () -> assertFalse(adapter.contains("ProgressiveMcpProcessManager")),
                () -> assertFalse(adapter.contains("McpCommands.RuntimeCall")),
                () -> assertFalse(adapter.contains("Callable<String>")),
                () -> assertFalse(adapter.contains("ToolContext")),
                () -> assertFalse(adapter.contains("SENSITIVE_KEY_PATTERN")),
                () -> assertFalse(adapter.contains("ObjectProvider")),
                // Session recovery belongs here; line count does not enforce the dispatch boundary.
                () -> assertFalse(adapter.contains("callback.call(")),
                () -> assertFalse(adapter.contains("client().callTool(")));
    }

    @Test
    void remoteInvocationAdapterMustOwnSerializationRedactionAndRuntimeAudit() throws IOException {
        String adapter = read(RUNTIME + "OpsMcpRemoteInvocationAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains("private final OpsMcpRemoteClientAdapter remoteClientAdapter;")),
                () -> assertTrue(adapter.contains("Supplier<ProgressiveMcpProcessManager>")),
                () -> assertTrue(adapter.contains("ToolCallback serialized(")),
                () -> assertTrue(adapter.contains("public String invoke(")),
                () -> assertTrue(adapter.contains("Callable<String> call")),
                () -> assertTrue(adapter.contains("OpsMcpRequestScope.acquire(handle.lock())")),
                () -> assertTrue(adapter.contains("handle.touch()")),
                () -> assertTrue(adapter.contains("SENSITIVE_KEY_PATTERN")),
                () -> assertTrue(adapter.contains("McpCommands.RuntimeCall")),
                () -> assertTrue(adapter.contains("summarizeInput")),
                () -> assertFalse(adapter.contains("OpsMcpClientRegistry clientRegistry")),
                () -> assertFalse(adapter.contains("OpsMcpClientFactory clientFactory")),
                () -> assertFalse(adapter.contains("ObjectProvider")),
                () -> assertFalse(adapter.contains("@Autowired")),
                () -> assertTrue(adapter.lines().count() < 300));
    }

    @Test
    void providerMustRemainThinOrchestratorWithoutClientHandleOrInvocationMechanics() throws IOException {
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");

        assertAll(
                () -> assertTrue(provider.contains("private final OpsMcpRuntimeInvoker runtimeInvoker;")),
                () -> assertTrue(provider.contains("runtimeInvoker.inspectDefinition(config, remoteToolName)")),
                () -> assertTrue(provider.contains("runtimeInvoker.inspectDefinitions(config)")),
                () -> assertTrue(provider.contains("runtimeInvoker.invoke(config, remoteToolName, remoteArgs)")),
                () -> assertTrue(provider.contains("runtimeInvoker.invalidateAll()")),
                () -> assertFalse(provider.contains("private final OpsMcpRemoteClientAdapter")),
                () -> assertFalse(provider.contains("private final OpsMcpRemoteInvocationAdapter")),
                () -> assertFalse(provider.contains("remoteClientAdapter.open(config)")),
                () -> assertFalse(provider.contains("remoteInvocationAdapter.serialized")),
                () -> assertFalse(provider.contains("OpsMcpClientRegistry")),
                () -> assertFalse(provider.contains("OpsMcpClientFactory")),
                () -> assertFalse(provider.contains("ClientHandle")),
                () -> assertFalse(provider.contains("import io.modelcontextprotocol.spec.McpSchema.Tool")),
                () -> assertFalse(provider.contains("import org.springframework.ai.tool.metadata.ToolMetadata")),
                () -> assertFalse(provider.contains("import org.springframework.ai.tool.ToolContext")),
                () -> assertFalse(provider.contains("Callable")),
                () -> assertFalse(provider.contains("SENSITIVE_KEY_PATTERN")),
                () -> assertFalse(provider.contains("private String callSerialized")),
                () -> assertFalse(provider.contains("private void recordMcpCall")),
                () -> assertFalse(provider.contains("private String safeInputSummary")),
                () -> assertTrue(provider.lines().count() < 180));
    }

    @Test
    void configurationMustOwnAdapterAssemblyAndLazyAuditDependency() throws IOException {
        String configuration = read(RUNTIME + "OpsMcpToolProviderConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains("OpsMcpRemoteClientAdapter opsMcpRemoteClientAdapter")),
                () -> assertTrue(configuration.contains("new OpsMcpRemoteCatalog(service,catalogs,clientFactory)")),
                () -> assertTrue(configuration.contains("OpsMcpRemoteInvocationAdapter opsMcpRemoteInvocationAdapter")),
                () -> assertTrue(configuration.contains("ObjectProvider<ProgressiveMcpProcessManager>")),
                () -> assertTrue(configuration.contains("progressiveMcpProcessManagerProvider::getIfAvailable")),
                () -> assertTrue(configuration.lines().count() < 50));
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
