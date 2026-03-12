package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageCleanupPublishedLanguageArchitectureTest {

    @Test
    void cleanupMustTranslateRepairResultIntoTypedChangePackageOutcome() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/ChangePackageCleanupPort.java");
        String useCase = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/ChangePackageCleanupUseCase.java");
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/changepackage/OpsChangePackageCleanupAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("ChangePackageRepairCleanupOutcome cleanupRepairWorkspace(")),
                () -> assertFalse(port.contains("Map<String, Object> cleanupRepairWorkspace(")),
                () -> assertTrue(useCase.contains("ChangePackageRepairCleanupOutcome cleanup")),
                () -> assertTrue(useCase.contains("cleanup.status()")),
                () -> assertFalse(useCase.contains("firstNonBlank(payload.get(\"status\")")),
                () -> assertTrue(adapter.contains("RepairCleanupResult cleanup")),
                () -> assertTrue(adapter.contains("new ChangePackageRepairCleanupOutcome(")),
                () -> assertFalse(adapter.contains("OpsRepairWorkspaceMapper")),
                () -> assertFalse(adapter.contains("Map<String, Object> cleanupRepairWorkspace")));
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
