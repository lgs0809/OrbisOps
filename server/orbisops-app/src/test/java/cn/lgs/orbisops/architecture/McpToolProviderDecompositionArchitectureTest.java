package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolProviderDecompositionArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void providerMustRemainCompatibilityFacadeOnly() throws IOException {
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");

        assertAll(
                () -> assertTrue(provider.contains("OpsMcpToolCallbackAssembler callbackAssembler")),
                () -> assertTrue(provider.contains("OpsProgressiveMcpInvocationService progressiveInvocations")),
                () -> assertTrue(provider.contains("OpsMcpRuntimeInvoker runtimeInvoker")),
                () -> assertTrue(provider.contains("return callbackAssembler.assemble(configs)")),
                () -> assertTrue(provider.contains("progressiveInvocations.invoke(")),
                () -> assertTrue(provider.contains("runtimeInvoker.inspectDefinition")),
                () -> assertTrue(provider.contains("runtimeInvoker.invalidateAll")),
                () -> assertFalse(provider.contains("JSON.parse")),
                () -> assertFalse(provider.contains("selectAndHydrate")),
                () -> assertFalse(provider.contains("AtomicBoolean")),
                () -> assertFalse(provider.contains("for (ToolCallback callback")),
                () -> assertFalse(provider.contains("recordPreRemoteFailure")));
    }

    @Test
    void extractedServicesMustOwnDistinctMcpResponsibilities() throws IOException {
        String assembler = read(RUNTIME + "OpsMcpToolCallbackAssembler.java");
        String progressive = read(RUNTIME + "OpsProgressiveMcpInvocationService.java");
        String invoker = read(RUNTIME + "OpsMcpRuntimeInvoker.java");
        String combined = assembler + progressive + invoker;

        assertAll(
                () -> assertTrue(assembler.contains("progressiveCallbacks.catalogCallback")),
                () -> assertTrue(assembler.contains("progressiveCallbacks.enableCallback")),
                () -> assertTrue(assembler.contains("progressiveCallbacks.dispatcherCallback")),
                () -> assertTrue(assembler.contains("callbackPolicy.decorate")),
                () -> assertFalse(assembler.contains("JSON.parse")),
                () -> assertFalse(assembler.contains("selectAndHydrate")),
                () -> assertTrue(progressive.contains("Map<String, Object> input = parse(toolInput)")),
                () -> assertTrue(progressive.contains("assertAuthorized(config, remoteToolName)")),
                () -> assertTrue(progressive.contains("progressiveRuntime.selectAndHydrate")),
                () -> assertTrue(progressive.contains("remoteCallPolicy.assertAllowed")),
                () -> assertTrue(progressive.contains("recordPreRemoteFailure")),
                () -> assertFalse(progressive.contains("catalogCallback")),
                () -> assertFalse(progressive.contains("List<ToolCallback>")),
                () -> assertTrue(invoker.contains("OpsMcpRemoteClientAdapter remoteClients")),
                () -> assertTrue(invoker.contains("OpsMcpRemoteInvocationAdapter remoteInvocations")),
                () -> assertTrue(invoker.contains("remoteInvocations.invoke")),
                () -> assertTrue(invoker.contains("remoteClients.invalidateAll")),
                () -> assertFalse(invoker.contains("OpsMcpRemoteCallPolicy")),
                () -> assertFalse(invoker.contains("OpsMcpCallbackPolicyAdapter")),
                () -> assertFalse(combined.contains("ToolExecutionPolicy")),
                () -> assertFalse(combined.contains("ToolExecutionRecordPort")),
                () -> assertFalse(combined.contains("ToolExecutionAuditPort")),
                () -> assertFalse(combined.contains("ChangePackagePolicy")));
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
