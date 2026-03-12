package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCompositeToolDispatchArchitectureTest {

    private static final String DISPATCH = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/toolexecution/dispatch/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/toolexecution/";

    @Test
    void mcpProviderMustEnterUnifiedToolMainlineThroughCompositeDispatch() throws IOException {
        String handler = read(DISPATCH + "OpsMcpToolExecutionDispatchHandler.java");
        String invoker = read(DISPATCH + "OpsMcpToolInvoker.java");
        String composite = read(DISPATCH + "OpsCompositeToolExecutionDispatchAdapter.java");
        String service = read(APPLICATION + "ToolExecutionApplicationService.java");

        assertAll(
                () -> assertTrue(handler.contains("implements OpsToolExecutionDispatchHandler")),
                () -> assertTrue(handler.contains("ToolProviderType.MCP")),
                () -> assertTrue(handler.contains("McpExecutionApplicationService mcpExecution")),
                () -> assertTrue(handler.contains("mcpExecution.execute(mcpRequest)")),
                () -> assertTrue(handler.contains("OpsProjectMcpRuntimeConfigService runtimeConfigs")),
                () -> assertTrue(handler.contains("MCP_RUNTIME_CONFIG_NOT_FOUND")),
                () -> assertTrue(handler.contains("providerResult")),
                () -> assertTrue(invoker.contains("implements ToolInvoker<McpToolBinding>")),
                () -> assertTrue(invoker.contains("handler.dispatch(target, prepared)")),
                () -> assertTrue(composite.contains("ToolBindingResolver bindingResolver")),
                () -> assertTrue(composite.contains("List<ToolInvoker<?>> invokers")),
                () -> assertTrue(composite.contains("bindingResolver.resolve(target)")),
                () -> assertFalse(composite.contains("compatibilityHandlers")),
                () -> assertFalse(composite.contains("compatibilityDispatch")),
                () -> assertTrue(service.contains("dispatchRequest(request, reservation, idempotencyKey, toolCallId)")),
                () -> assertTrue(service.contains("fencingToken")),
                () -> assertTrue(service.contains("TOOL_EXECUTION_STARTED")),
                () -> assertTrue(service.contains("TOOL_EXECUTION_COMPLETED")),
                () -> assertTrue(service.contains("TOOL_EXECUTION_FAILED")));
    }

    @Test
    void mcpHandlerMustNotDuplicateTopLevelGovernance() throws IOException {
        String handler = read(DISPATCH + "OpsMcpToolExecutionDispatchHandler.java");

        assertAll(
                () -> assertFalse(handler.contains("ToolExecutionPolicy")),
                () -> assertFalse(handler.contains("ToolExecutionCheckpointPort")),
                () -> assertFalse(handler.contains("ToolExecutionRecordPort")),
                () -> assertFalse(handler.contains("ToolExecutionAuditPort")),
                () -> assertFalse(handler.contains("ChangePackagePolicy")),
                () -> assertFalse(handler.contains("CanonicalObjectHasher")),
                () -> assertFalse(handler.contains("ToolExecutionApplicationService")));
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
