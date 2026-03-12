package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpProgressiveRuntimeArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void settingsAndExposureMustRemainTypedAndIndependentFromRuntimeServices() throws IOException {
        String settings = read(RUNTIME + "OpsMcpProgressiveSettings.java");
        String exposure = read(RUNTIME + "OpsMcpProgressiveExposure.java");

        assertAll(
                () -> assertTrue(settings.contains("final class OpsMcpProgressiveSettings")),
                () -> assertTrue(settings.contains("orbisops.progressive-mcp.enforce-project-managed")),
                () -> assertTrue(settings.contains("orbisops.progressive-mcp.disclosure.enabled")),
                () -> assertTrue(settings.contains("forTest")),
                () -> assertFalse(settings.contains("ObjectProvider")),
                () -> assertFalse(settings.contains("ProgressiveMcpProcessManager")),
                () -> assertTrue(settings.lines().count() < 50),
                () -> assertTrue(exposure.contains("record OpsMcpProgressiveExposure")),
                () -> assertTrue(exposure.contains("enum Mode")),
                () -> assertTrue(exposure.contains("allowedToolNames")),
                () -> assertTrue(exposure.contains("extensionAvailable")),
                () -> assertFalse(exposure.contains("ProgressiveMcpProcessManager")),
                () -> assertFalse(exposure.contains("McpCommands")),
                () -> assertTrue(exposure.lines().count() < 80));
    }

    @Test
    void progressiveRuntimeAdapterMustOwnExposureRoutingHydrationWarningsAndPreCallAudit() throws IOException {
        String adapter = read(RUNTIME + "OpsMcpProgressiveRuntimeAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains("Supplier<ProgressiveMcpProcessManager>")),
                () -> assertTrue(adapter.contains("OpsMcpProgressiveExposure exposure")),
                () -> assertTrue(adapter.contains("runtimeExecutableTools")),
                () -> assertTrue(adapter.contains("authorizedForCurrentStage")),
                () -> assertTrue(adapter.contains("recordToolRoutingWarning")),
                () -> assertTrue(adapter.contains("Map<String, Object> selectAndHydrate")),
                () -> assertTrue(adapter.contains("processManager.select")),
                () -> assertTrue(adapter.contains("processManager.hydrateSchema")),
                () -> assertTrue(adapter.contains("recordPreRemoteFailure")),
                () -> assertTrue(adapter.contains("McpCommands.RuntimeCall")),
                () -> assertFalse(adapter.contains("ObjectProvider")),
                () -> assertFalse(adapter.contains("@Value")),
                () -> assertFalse(adapter.contains("OpsMcpClientRegistry")),
                () -> assertFalse(adapter.contains("OpsMcpRemoteInvocationAdapter")),
                () -> assertFalse(adapter.contains("ToolCallback")),
                () -> assertTrue(adapter.lines().count() < 240));
    }

    @Test
    void providerMustDelegateProgressiveRuntimeAndNeverReclaimSettingsOrProcessManager() throws IOException {
        String assembler = read(RUNTIME + "OpsMcpToolCallbackAssembler.java");
        String progressive = read(RUNTIME + "OpsProgressiveMcpInvocationService.java");
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");

        assertAll(
                () -> assertTrue(assembler.contains("private final OpsMcpProgressiveRuntimeAdapter progressiveRuntime;")),
                () -> assertTrue(assembler.contains("progressiveRuntime.exposure(config)")),
                () -> assertTrue(progressive.contains("private final OpsMcpProgressiveRuntimeAdapter progressiveRuntime;")),
                () -> assertTrue(progressive.contains("progressiveRuntime.selectAndHydrate(config, remoteToolName)")),
                () -> assertTrue(progressive.contains("progressiveRuntime.recordPreRemoteFailure(")),
                () -> assertFalse(provider.contains("private final OpsMcpProgressiveRuntimeAdapter")),
                () -> assertFalse(provider.contains("ProgressiveMcpProcessManager")),
                () -> assertFalse(provider.contains("ObjectProvider")),
                () -> assertFalse(provider.contains("@Value")),
                () -> assertFalse(provider.contains("McpCommands")),
                () -> assertFalse(provider.contains("orbisops.progressive-mcp.enforce-project-managed")),
                () -> assertFalse(provider.contains("orbisops.progressive-mcp.disclosure.enabled")),
                () -> assertFalse(provider.contains("runtimeExecutableTools")),
                () -> assertFalse(provider.contains("recordToolRoutingWarning")),
                () -> assertTrue(provider.lines().count() < 180));
    }

    @Test
    void configurationMustOwnLazyProgressiveRuntimeAssembly() throws IOException {
        String configuration = read(RUNTIME + "OpsMcpToolProviderConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains("OpsMcpProgressiveRuntimeAdapter opsMcpProgressiveRuntimeAdapter")),
                () -> assertTrue(configuration.contains("ObjectProvider<ProgressiveMcpProcessManager>")),
                () -> assertTrue(configuration.contains("new OpsMcpProgressiveRuntimeAdapter(")),
                () -> assertTrue(configuration.contains("progressiveMcpProcessManagerProvider::getIfAvailable")),
                () -> assertTrue(configuration.contains("OpsMcpProgressiveSettings settings")),
                () -> assertTrue(configuration.lines().count() < 60));
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
