package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRuntimeResilienceArchitectureTest {

    private static final String DOMAIN =
            "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/evidence/service/";
    private static final String RUNTIME =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void mcpInvocationMustUseSharedResilienceRedactionAndLowCardinalityTelemetry()
            throws IOException {
        String invocation = read(RUNTIME + "OpsMcpRemoteInvocationAdapter.java");
        String resilience = read(RUNTIME + "OpsMcpInvocationResiliencePolicy.java");
        String telemetry = read(RUNTIME + "OpsMcpRuntimeSloTelemetry.java");
        String configuration = read(RUNTIME + "OpsMcpToolProviderConfiguration.java");
        String redaction = read(DOMAIN + "SensitiveDataRedactionPolicy.java");
        String health = read("orbisops-app/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsMcpRuntimeHealthIndicator.java");

        assertAll(
                () -> assertTrue(invocation.contains("resilience.acquire")),
                () -> assertTrue(invocation.contains("resilience.succeeded")),
                () -> assertTrue(invocation.contains("resilience.failed")),
                () -> assertTrue(invocation.contains("REDACTION.redact(output)")),
                () -> assertTrue(invocation.contains("\"REJECTED\"")),
                () -> assertFalse(invocation.contains(
                        "recordMcpCall(handle, toolName, \"SUCCEEDED\", toolInput, result")),
                () -> assertTrue(resilience.contains("halfOpenInFlight")),
                () -> assertTrue(resilience.contains("MCP_RESILIENCE_PERMIT_FENCED")),
                () -> assertTrue(configuration.contains(
                        "OpsMcpInvocationResiliencePolicy resilience")),
                () -> assertTrue(telemetry.contains("orbisops.mcp.invocations")),
                () -> assertTrue(telemetry.contains("orbisops.mcp.invocation.duration")),
                () -> assertTrue(telemetry.contains("orbisops.mcp.circuits.open")),
                () -> assertFalse(telemetry.contains("projectId")),
                () -> assertFalse(telemetry.contains("mcpId")),
                () -> assertTrue(redaction.contains("IdentityHashMap")),
                () -> assertTrue(redaction.contains("MAX_DEPTH")),
                () -> assertTrue(redaction.contains("QUERY_SECRET")),
                () -> assertTrue(health.contains("Health.outOfService")),
                () -> assertTrue(health.contains("Health.down")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
