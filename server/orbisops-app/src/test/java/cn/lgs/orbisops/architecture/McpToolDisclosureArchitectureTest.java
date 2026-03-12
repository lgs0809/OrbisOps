package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolDisclosureArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";
    private static final String SERVICE = TRIGGER + "ops/toolset/OpsToolExecutionService.java";

    @Test
    void disclosureEntriesMustShareTheRemoteExecutionApplication() throws IOException {
        String service = read(SERVICE);
        String section = service.substring(
                service.indexOf("public Map<String, Object> discoverMcpTools("));
        assertAll(
                () -> assertTrue(section.contains("mcpExecution.discover(")),
                () -> assertTrue(section.contains("mcpExecution.activate(")),
                () -> assertTrue(section.contains("toolExecution.execute(toolRequest)")),
                () -> assertFalse(section.contains("mcpExecution.execute(")),
                () -> assertTrue(section.contains("mcpMapper.request(")),
                () -> assertFalse(service.contains("McpToolDisclosureApplicationService")),
                () -> assertFalse(service.contains("McpToolDisclosureResult")),
                () -> assertFalse(service.contains("mcpToolDisclosureMapper")),
                () -> assertFalse(service.contains("disclosure()")));
    }

    @Test
    void sharedApplicationMustOwnDiscoverActivateAndExecute() throws IOException {
        String application = read(APPLICATION + "mcpexecution/McpExecutionApplicationService.java");
        String runtime = read(APPLICATION + "mcpexecution/McpExecutionRuntimePort.java");
        String remote = read(APPLICATION + "mcpexecution/McpExecutionRemotePort.java");
        assertAll(
                () -> assertTrue(application.contains("McpExecutionResponse discover(")),
                () -> assertTrue(application.contains("McpExecutionResponse activate(")),
                () -> assertTrue(application.contains("McpExecutionResponse execute(")),
                () -> assertTrue(application.contains("recordResponse(")),
                () -> assertTrue(runtime.contains("McpRuntimeCatalog catalog(")),
                () -> assertTrue(runtime.contains("McpRuntimeActivation activate(")),
                () -> assertTrue(runtime.contains("McpRuntimeToolSchema schema(")),
                () -> assertTrue(runtime.contains("McpRuntimeToolSchema hydrate(")),
                () -> assertFalse(runtime.contains("List<Map<String, Object>> catalog(")),
                () -> assertFalse(runtime.contains("Map<String, Object> activate(")),
                () -> assertTrue(remote.contains("Map<String, Object> inspect(")),
                () -> assertTrue(remote.contains("String call(")));
    }

    @Test
    void legacyDisclosureStackMustRemainPhysicallyDeleted() {
        assertAll(
                () -> assertMissing(DOMAIN + "mcp/model/McpToolDisclosureRequest.java"),
                () -> assertMissing(DOMAIN + "mcp/model/McpRemoteEndpoint.java"),
                () -> assertMissing(DOMAIN + "mcp/service/McpToolDisclosurePolicy.java"),
                () -> assertMissing(APPLICATION + "mcp/McpToolDisclosureApplicationService.java"),
                () -> assertMissing(APPLICATION + "mcp/McpToolDisclosureResult.java"),
                () -> assertMissing(APPLICATION + "mcp/McpToolDisclosureRuntimePort.java"),
                () -> assertMissing(APPLICATION + "mcp/McpRemoteToolDefinitionPort.java"),
                () -> assertMissing(TRIGGER + "application/mcp/OpsMcpToolDisclosureApplicationConfiguration.java"),
                () -> assertMissing(TRIGGER + "application/mcp/OpsMcpToolDisclosureMapper.java"),
                () -> assertMissing(TRIGGER + "application/mcp/OpsMcpToolDisclosureRuntimeAdapter.java"),
                () -> assertMissing(TRIGGER + "application/mcp/OpsMcpRemoteToolDefinitionAdapter.java"));
    }

    private void assertMissing(String relativePath) {
        assertFalse(Files.exists(projectRoot().resolve(relativePath)), relativePath + " must not return");
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
