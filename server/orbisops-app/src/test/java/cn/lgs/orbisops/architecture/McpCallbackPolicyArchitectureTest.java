package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCallbackPolicyArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void callbackPolicyAdapterMustOwnCapabilityAclDecorationAndCompatibilityPolicy() throws IOException {
        String adapter = read(RUNTIME + "OpsMcpCallbackPolicyAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains("private final OpsToolExecutionPolicy toolExecutionPolicy;")),
                () -> assertTrue(adapter.contains("declaredCapability")),
                () -> assertTrue(adapter.contains("matchesTool")),
                () -> assertTrue(adapter.contains("ToolCallback decorate")),
                () -> assertTrue(adapter.contains("void assertAllowed")),
                () -> assertTrue(adapter.contains("toolExecutionPolicy.allowTool")),
                () -> assertTrue(adapter.contains("toolExecutionPolicy.blockedReason")),
                () -> assertFalse(adapter.contains("ObjectProvider")),
                () -> assertFalse(adapter.contains("@Autowired")),
                () -> assertFalse(adapter.contains("ProgressiveMcpProcessManager")),
                () -> assertFalse(adapter.contains("OpsMcpClientRegistry")),
                () -> assertTrue(adapter.lines().count() < 150));
    }

    @Test
    void providerMustDelegateCallbackAclAndNeverReclaimCompatibilityPolicy() throws IOException {
        String assembler = read(RUNTIME + "OpsMcpToolCallbackAssembler.java");
        String progressive = read(RUNTIME + "OpsProgressiveMcpInvocationService.java");
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");

        assertAll(
                () -> assertTrue(assembler.contains("private final OpsMcpCallbackPolicyAdapter callbackPolicy;")),
                () -> assertTrue(assembler.contains("callbackPolicy.declaredCapability(config, callback)")),
                () -> assertTrue(assembler.contains("callbackPolicy.allowed(callback, capability)")),
                () -> assertTrue(assembler.contains("callbackPolicy.decorate(")),
                () -> assertTrue(assembler.contains("OpsRuntimeGovernedToolCallback.wrap")),
                () -> assertTrue(assembler.contains("OpsRuntimeToolAuthorityDescriptor.delegated")),
                () -> assertTrue(progressive.contains("callbackPolicy.assertAllowed(verified, runtimeInvoker.find")),
                () -> assertTrue(progressive.contains("callbackPolicy.matchesTool")),
                () -> assertFalse(provider.contains("private final OpsMcpCallbackPolicyAdapter")),
                () -> assertFalse(provider.contains("OpsToolExecutionPolicy")),
                () -> assertFalse(provider.contains("private ToolCallback capabilityAwareCallback")),
                () -> assertFalse(provider.contains("private String declaredCapability")),
                () -> assertFalse(provider.contains("private boolean matchesTool")),
                () -> assertFalse(provider.contains("Ops capability:")),
                () -> assertTrue(assembler.lines().count() < 220),
                () -> assertTrue(progressive.lines().count() < 180));
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
