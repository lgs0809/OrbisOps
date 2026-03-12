package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageTypedReadinessBoundaryArchitectureTest {

    @Test
    void readinessMustRemainTypedUntilDiagnosticProjection() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/changepackage/ChangePackageReadinessPort.java");
        String jdbc = read("orbisops-infrastructure/src/main/java/"
                + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChangePackageReadinessAdapter.java");
        String environment = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsCapabilityReadinessEnvironmentAdapter.java");
        String health = read("orbisops-app/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsSafetyReadinessHealthIndicator.java");

        assertAll(
                () -> assertTrue(port.contains("ChangePackageReadinessSnapshot readiness()")),
                () -> assertFalse(port.contains("Map<String, Object> readiness()")),
                () -> assertTrue(jdbc.contains("ChangePackageReadinessSnapshot.up(")),
                () -> assertFalse(jdbc.contains("data.put(\"status\"")),
                () -> assertTrue(environment.contains("snapshot.up()")),
                () -> assertTrue(environment.contains("snapshot.reason()")),
                () -> assertFalse(environment.contains("changePackageReadiness::readiness")),
                () -> assertTrue(health.contains("return snapshot.up()")),
                () -> assertTrue(health.contains("snapshot.details()")));
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
