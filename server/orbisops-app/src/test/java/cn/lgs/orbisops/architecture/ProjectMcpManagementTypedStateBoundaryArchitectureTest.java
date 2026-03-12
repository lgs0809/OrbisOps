package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMcpManagementTypedStateBoundaryArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/project/";

    @Test
    void mcpManagementMustUpdateTypedDefinitionWithoutCompatibilityRoundTrip() throws IOException {
        String service = read(APPLICATION + "ProjectMcpManagementApplicationService.java");
        String processManager = read(APPLICATION + "ManageProjectWorkspaceUseCase.java");

        assertAll(
                () -> assertTrue(service.contains("ProjectMcpDefinition updateDefinition(")),
                () -> assertTrue(service.contains("ProjectMcpDefinition updateStatusDefinition(")),
                () -> assertTrue(service.contains("McpRiskLevel targetRiskLevel")),
                () -> assertTrue(service.contains("ProjectMcpStatus targetStatus")),
                () -> assertTrue(service.contains("current.update(")),
                () -> assertTrue(service.contains("return catalogService.view(updateDefinition(")),
                () -> assertFalse(service.contains("catalogService.view(current)")),
                () -> assertFalse(service.contains("Map<String, Object> definition =")),
                () -> assertFalse(service.contains("definition.put(")),
                () -> assertFalse(service.contains("catalogService.save(definition)")),
                () -> assertTrue(processManager.contains("mcpManagementService.updateDefinition(")),
                () -> assertTrue(processManager.contains("mcpManagementService.updateStatusDefinition(")),
                () -> assertFalse(processManager.contains("mcpManagementService.update(id, target")),
                () -> assertFalse(processManager.contains("mcpManagementService.updateStatus(\n")));
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
