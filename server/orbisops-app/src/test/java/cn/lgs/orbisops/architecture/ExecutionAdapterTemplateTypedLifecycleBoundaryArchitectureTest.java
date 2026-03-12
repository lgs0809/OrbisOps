package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAdapterTemplateTypedLifecycleBoundaryArchitectureTest {

    @Test
    void templateCrudAndStatusMustRemainTypedUntilCompatibilityProjection() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/execution/ExecutionAdapterTemplatePort.java");
        String service = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/execution/ExecutionAdapterTemplateApplicationService.java");
        String catalog = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/execution/ExecutionAdapterTemplateCatalogApplicationService.java");
        String view = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/execution/ExecutionAdapterTemplateView.java");
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/execution/OpsExecutionAdapterTemplateAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("List<ExecutionAdapterTemplate> listTemplates()")),
                () -> assertTrue(port.contains("ExecutionAdapterTemplate getTemplate(")),
                () -> assertTrue(port.contains("ExecutionAdapterTemplate createTemplate(")),
                () -> assertTrue(port.contains("ExecutionAdapterTemplate updateTemplate(")),
                () -> assertTrue(port.contains("ExecutionAdapterTemplate updateTemplateStatus(")),
                () -> assertTrue(port.contains("ExecutionAdapterTemplate copyTemplate(")),
                () -> assertFalse(port.contains("List<Map<String, Object>> list")),
                () -> assertFalse(port.contains("Map<String, Object> get(")),
                () -> assertFalse(port.contains("Map<String, Object> create(")),
                () -> assertTrue(port.contains("List<Map<String, Object>> generatedTargets")),
                () -> assertTrue(service.contains("port.getTemplate(")),
                () -> assertTrue(service.contains("port.createTemplate(")),
                () -> assertTrue(service.contains("port.updateTemplateStatus(")),
                () -> assertFalse(service.contains("before = port.get(")),
                () -> assertTrue(catalog.contains("ExecutionAdapterTemplate createTemplate(")),
                () -> assertTrue(catalog.contains("ExecutionAdapterTemplateView.of(")),
                () -> assertTrue(view.contains("generatedTargetCount")),
                () -> assertTrue(adapter.contains("catalogService.createTemplate(command)")),
                () -> assertFalse(adapter.contains("Map<String, Object> create(")));
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
