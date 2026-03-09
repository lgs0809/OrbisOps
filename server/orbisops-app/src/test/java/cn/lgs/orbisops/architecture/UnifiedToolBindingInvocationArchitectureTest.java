package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedToolBindingInvocationArchitectureTest {

    @Test
    void upperExecutionMustResolveBindingAndUseProviderNeutralCoordinator() throws IOException {
        String binding = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/toolset/model/ToolBinding.java");
        String governance = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/toolset/model/ToolGovernance.java");
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/toolexecution/ToolInvocationPort.java");
        String coordinator = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/toolexecution/ToolInvocationCoordinator.java");
        String composite = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/toolexecution/dispatch/"
                + "OpsCompositeToolExecutionDispatchAdapter.java");

        assertAll(
                () -> assertTrue(binding.contains("sealed interface ToolBinding")),
                () -> assertTrue(binding.contains("InternalToolBinding")),
                () -> assertTrue(binding.contains("McpToolBinding")),
                () -> assertFalse(binding.contains("SandboxToolBinding")),
                () -> assertTrue(governance.contains("ToolEffect effect")),
                () -> assertTrue(governance.contains("ApprovalRequirement approvalRequirement")),
                () -> assertTrue(governance.contains("ReconciliationCapability reconciliationCapability")),
                () -> assertTrue(port.contains("ToolInvocationResult invoke(ToolInvocation invocation)")),
                () -> assertTrue(coordinator.contains("ToolExecutionApplicationService execution")),
                () -> assertTrue(coordinator.contains("execution.execute(invocation)")),
                () -> assertTrue(composite.contains("bindingResolver.resolve(target)")),
                () -> assertTrue(composite.contains("List<ToolInvoker<?>> invokers")),
                () -> assertFalse(composite.contains("providerType() == ToolProviderType.MCP")),
                () -> assertFalse(composite.contains("startsWith(\"LOCAL_\")")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
