package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpProgressiveCallbackArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void callbackAdapterMustOwnProgressiveSchemasAndUnifiedExecutionCompatibility() throws IOException {
        String adapter = read(RUNTIME + "OpsProgressiveMcpCallbackAdapter.java");
        String executor = read(RUNTIME + "OpsMcpUnifiedToolExecutor.java");

        assertAll(
                () -> assertTrue(adapter.contains("Supplier<OpsToolExecutionService>")),
                () -> assertTrue(adapter.contains("dispatcherCallback")),
                () -> assertTrue(adapter.contains("catalogCallback")),
                () -> assertTrue(adapter.contains("enableCallback")),
                () -> assertTrue(adapter.contains("directCallback")),
                () -> assertTrue(adapter.contains("unifiedExecutor.executeDispatcher")),
                () -> assertTrue(adapter.contains("unifiedExecutor.executeDirect")),
                () -> assertTrue(adapter.contains("discoverMcpTools(config, \"ops-agent\")")),
                () -> assertTrue(adapter.contains("enableMcpTool(config, input, \"ops-agent\")")),
                () -> assertTrue(executor.contains("executeMcp(config, toolInput, \"ops-agent\",")),
                () -> assertTrue(executor.contains("executeLandingMcp")),
                () -> assertTrue(executor.contains("CanonicalObjectHasher.sha256")),
                () -> assertTrue(executor.contains("agentObservation")),
                () -> assertFalse(adapter.contains("ObjectProvider")),
                () -> assertFalse(adapter.contains("@Autowired")),
                () -> assertTrue(adapter.lines().count() < 220),
                () -> assertTrue(executor.lines().count() < 220));
    }

    @Test
    void springConfigurationMustOwnLazyToolExecutionResolution() throws IOException {
        String configuration = read(RUNTIME + "OpsMcpToolProviderConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains("ObjectProvider<OpsToolExecutionService>")),
                () -> assertTrue(configuration.contains("toolExecutionServiceProvider::getIfAvailable")),
                () -> assertTrue(configuration.contains("OpsProgressiveMcpCallbackAdapter")),
                () -> assertTrue(configuration.contains(
                        "new OpsMcpRemoteCatalog(service,catalogs,clientFactory)")),
                () -> assertTrue(configuration.lines().count() < 60));
    }

    @Test
    void providerMustDelegateCallbacksAndNeverReclaimToolExecutionFieldInjection() throws IOException {
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");

        assertAll(
                () -> assertTrue(provider.contains("private final OpsMcpToolCallbackAssembler callbackAssembler;")),
                () -> assertTrue(provider.contains("private final OpsProgressiveMcpInvocationService progressiveInvocations;")),
                () -> assertTrue(provider.contains("private final OpsMcpRuntimeInvoker runtimeInvoker;")),
                () -> assertTrue(provider.contains("return callbackAssembler.assemble(configs)")),
                () -> assertTrue(provider.contains("progressiveInvocations.invoke(")),
                () -> assertTrue(provider.contains("runtimeInvoker.inspectDefinition")),
                () -> assertFalse(provider.contains("private OpsToolExecutionService toolExecutionService")),
                () -> assertFalse(provider.contains("@Autowired(required = false)")),
                () -> assertFalse(provider.contains("progressiveCallbackAdapter.catalogCallback")),
                () -> assertFalse(provider.contains("progressiveCallbackAdapter.enableCallback")),
                () -> assertFalse(provider.contains("progressiveCallbackAdapter.dispatcherCallback")),
                () -> assertFalse(provider.contains("selectAndHydrate")),
                () -> assertFalse(provider.contains("agentObservation")),
                () -> assertFalse(provider.contains("reasonCode(")),
                () -> assertTrue(provider.lines().count() < 180));
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
