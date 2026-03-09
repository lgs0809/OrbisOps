package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMcpTemplateGenerationBoundaryArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/project/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/project/";

    @Test
    void templateGenerationMustUseTypedResourceFactsAndTypedCatalogSave() throws IOException {
        String service = read(APPLICATION + "ProjectMcpTemplateGenerationApplicationService.java");
        String request = read(APPLICATION + "ProjectMcpTemplateGenerationPreparationRequest.java");
        String factory = read(TRIGGER + "OpsProjectMcpGenerationPreparationFactory.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "ProjectResourceDefinition resource = resourceService.requireResource")),
                () -> assertTrue(service.contains("new ProjectMcpDefinition(")),
                () -> assertTrue(service.contains("catalogService.save(definition)")),
                () -> assertFalse(service.contains("resourceService.findView")),
                () -> assertFalse(service.contains("resource.get(\"type\")")),
                () -> assertFalse(service.contains("resource.get(\"environment\")")),
                () -> assertFalse(service.contains("Map<String, Object> definition =")),
                () -> assertTrue(request.contains("ProjectResourceDefinition resource")),
                () -> assertTrue(request.contains("PROJECT_MCP_RESOURCE_BINDING_MISMATCH")),
                () -> assertFalse(request.contains("Map<String, Object> resource")),
                () -> assertTrue(factory.contains("resource.type().value()")),
                () -> assertTrue(factory.contains("resource.permission()")),
                () -> assertTrue(factory.contains("resource.endpoint()")),
                () -> assertTrue(factory.contains("resource.credential()")),
                () -> assertTrue(factory.contains("resource.schema()")),
                () -> assertFalse(factory.contains("resource.get(")),
                () -> assertFalse(factory.contains("request.resource().isEmpty()")));
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
