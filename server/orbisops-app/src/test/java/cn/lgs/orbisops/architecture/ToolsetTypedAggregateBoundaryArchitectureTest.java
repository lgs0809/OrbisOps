package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolsetTypedAggregateBoundaryArchitectureTest {

    @Test
    void toolsetOwnershipMustRemainInDomainAndTypedApplicationPort() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/toolset/ToolsetCatalogPort.java");
        String service = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/toolset/ToolsetApplicationService.java");
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/toolset/OpsToolsetCatalogAdapter.java");
        String controller = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/http/admin/OpsToolsetAdminController.java");
        String domain = read("orbisops-domain/src/main/java/"
                + "cn/lgs/orbisops/domain/toolset/model/ToolsetDefinition.java");

        assertAll(
                () -> assertTrue(port.contains("List<ToolsetDefinition> listBuiltIn()")),
                () -> assertTrue(port.contains("ToolsetDefinition registerCustom(")),
                () -> assertTrue(port.contains("ToolsetDefinition setEnabled(")),
                () -> assertTrue(port.contains("ToolsetRefreshOutcome refreshMcp(")),
                () -> assertFalse(port.contains("ToolsetCatalogPort<D>")),
                () -> assertFalse(port.contains("Map<String, Object> describe(")),
                () -> assertFalse(port.contains("String id(")),
                () -> assertTrue(service.contains("ToolsetDefinition before")),
                () -> assertTrue(service.contains("ToolsetActivationOutcome")),
                () -> assertTrue(service.contains("ToolsetRefreshOutcome")),
                () -> assertFalse(service.contains("ToolsetApplicationService<D>")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(adapter.contains("implements ToolsetCatalogPort")),
                () -> assertTrue(adapter.contains("new ToolsetDefinition(")),
                () -> assertTrue(adapter.contains("new ToolDefinition(")),
                () -> assertTrue(controller.contains("Response<List<ToolsetDefinition>>")),
                () -> assertFalse(controller.contains("OpsToolsetDefinition")),
                () -> assertTrue(domain.contains("List<ToolDefinition> tools")));
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
