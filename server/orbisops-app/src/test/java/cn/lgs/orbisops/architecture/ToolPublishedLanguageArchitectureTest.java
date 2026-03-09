package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPublishedLanguageArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/toolexecution/";

    @Test
    void toolMustBeDomainAbstractionAndMcpMustRemainAProvider() throws IOException {
        String definition = read(DOMAIN + "toolset/model/ToolDefinition.java");
        String provider = read(DOMAIN + "toolset/model/ToolProviderDescriptor.java");
        String providerType = read(DOMAIN + "toolset/model/ToolProviderType.java");
        String reference = read(DOMAIN + "toolset/model/ToolReference.java");
        String bound = read(DOMAIN + "toolset/model/BoundToolReference.java");
        String invocation = read(DOMAIN + "toolexecution/model/ToolInvocation.java");
        String context = read(DOMAIN + "toolexecution/model/ToolInvocationContext.java");
        String result = read(DOMAIN + "toolexecution/model/ToolExecutionResult.java");
        String service = read(APPLICATION + "ToolExecutionApplicationService.java");

        assertAll(
                () -> assertTrue(definition.contains("ToolProviderDescriptor providerDescriptor")),
                () -> assertTrue(definition.contains("ToolSemantics semantics")),
                () -> assertTrue(definition.contains("ToolSchema schema")),
                () -> assertTrue(definition.contains("ToolGovernance governance")),
                () -> assertTrue(providerType.contains("MCP")),
                () -> assertTrue(provider.contains("ToolProviderType providerType")),
                () -> assertFalse(provider.contains("String credential")),
                () -> assertFalse(provider.contains("McpClient")),
                () -> assertFalse(provider.contains("String session")),
                () -> assertTrue(reference.contains("String toolsetId")),
                () -> assertTrue(reference.contains("String toolName")),
                () -> assertTrue(bound.contains("ToolProviderDescriptor provider")),
                () -> assertTrue(bound.contains("ToolSemantics semantics")),
                () -> assertTrue(bound.contains("ToolSchema schema")),
                () -> assertTrue(bound.contains("ToolGovernance governance")),
                () -> assertTrue(invocation.contains("ToolReference reference")),
                () -> assertTrue(invocation.contains("Duration timeout")),
                () -> assertTrue(context.contains("Map<String, Object> landingAuthority")),
                () -> assertTrue(result.contains("ToolErrorCategory errorCategory")),
                () -> assertTrue(result.contains("ToolExecutionRecordedResult recorded")),
                () -> assertTrue(service.contains("public ToolExecutionResult execute(ToolInvocation invocation)")),
                () -> assertTrue(service.contains("return execute(request).result(")));
    }

    @Test
    void mcpProtocolAndControlPlaneModelsMustNotBeMovedIntoToolReference() throws IOException {
        String reference = read(DOMAIN + "toolset/model/ToolReference.java");
        String bound = read(DOMAIN + "toolset/model/BoundToolReference.java");

        assertAll(
                () -> assertFalse(reference.contains("String endpoint")),
                () -> assertFalse(reference.contains("String credential")),
                () -> assertFalse(reference.contains("String transport")),
                () -> assertFalse(reference.contains("String session")),
                () -> assertFalse(bound.contains("String endpoint")),
                () -> assertFalse(bound.contains("String credential")),
                () -> assertFalse(bound.contains("ToolCallback")),
                () -> assertFalse(bound.contains("McpClient")));
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
