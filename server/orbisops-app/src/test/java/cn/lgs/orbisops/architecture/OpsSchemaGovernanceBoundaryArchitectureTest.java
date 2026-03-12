package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSchemaGovernanceBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void schemaGovernanceServiceDelegatesTypedStateAndStableProjection() throws IOException {
        String service = read(OPS + "OpsSchemaGovernanceService.java");
        String settings = read(OPS + "OpsSchemaGovernanceSettings.java");
        String factory = read(OPS + "OpsSchemaGovernanceSnapshotFactory.java");
        String configuration = read(APPLICATION + "OpsSchemaGovernanceConfiguration.java");

        assertAll(
                () -> assertTrue(service.contains("OpsSchemaGovernanceSettings settings")),
                () -> assertTrue(service.contains("OpsSchemaGovernanceSnapshotFactory snapshotFactory")),
                () -> assertTrue(service.contains("public OpsSchemaGovernanceService()")),
                () -> assertTrue(service.contains("snapshotFactory.create(settings)")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("LinkedHashMap")),
                () -> assertFalse(service.contains("migrationFiles")),
                () -> assertTrue(service.lines().count() <= 40),
                () -> assertTrue(settings.contains("public record OpsSchemaGovernanceSettings(")),
                () -> assertTrue(settings.contains("anyAutoInitEnabled()")),
                () -> assertTrue(factory.contains("MIGRATION_FILES")),
                () -> assertTrue(factory.contains("MANUAL_MIGRATION_REQUIRED")),
                () -> assertFalse(factory.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.agents.auto-init")),
                () -> assertTrue(configuration.contains("orbisops.rag.ingestion.auto-init")));
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
