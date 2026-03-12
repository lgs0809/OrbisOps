package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTemplateTypedLifecycleBoundaryArchitectureTest {

    @Test
    void templateLifecycleMustRemainTypedUntilCompatibilityProjection() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/mcp/McpTemplatePort.java");
        String management = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/mcp/ManageMcpTemplateUseCase.java");
        String query = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/mcp/QueryMcpTemplateUseCase.java");
        String generation = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/project/ProjectMcpTemplateGenerationApplicationService.java");
        String request = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/project/ProjectMcpTemplateGenerationPreparationRequest.java");
        String preparation = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/project/OpsProjectMcpGenerationPreparationFactory.java");

        assertAll(
                () -> assertTrue(port.contains("List<McpTemplateCatalogEntry> listEntries()")),
                () -> assertTrue(port.contains("McpTemplateCatalogEntry getEntry")),
                () -> assertTrue(port.contains("createDefinition(McpTemplateDefinition definition)")),
                () -> assertTrue(port.contains("updateStatusDefinition(String templateId, McpTemplateStatus status)")),
                () -> assertFalse(port.contains("List<Map<String, Object>>")),
                () -> assertFalse(port.contains("Map<String, Object> get(")),
                () -> assertFalse(port.contains("Map<String, Object> create(")),
                () -> assertTrue(management.contains("port.getEntry(")),
                () -> assertTrue(management.contains("port.createDefinition(")),
                () -> assertTrue(management.contains("port.updateStatusDefinition(")),
                () -> assertFalse(management.contains("current.get(")),
                () -> assertFalse(management.contains("port.get(")),
                () -> assertTrue(query.contains("McpTemplateCatalogEntry")),
                () -> assertTrue(query.contains("McpTemplateCatalogView.of(")),
                () -> assertTrue(generation.contains("McpTemplateDefinition template")),
                () -> assertTrue(request.contains("McpTemplateDefinition template")),
                () -> assertFalse(generation.contains("templateDefinition.get(")),
                () -> assertFalse(preparation.contains("template.get(")));
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
