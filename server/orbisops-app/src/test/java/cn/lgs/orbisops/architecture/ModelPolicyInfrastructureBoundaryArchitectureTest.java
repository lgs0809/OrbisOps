package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPolicyInfrastructureBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/";
    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/";

    @Test
    void triggerModelPolicyServiceMustRemainAPortFacade() throws IOException {
        String service = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/OpsModelDefaultPolicyService.java");

        assertAll(
                () -> assertTrue(service.contains("ModelDefaultPolicyApplicationService")),
                () -> assertTrue(service.contains("policies.get(")),
                () -> assertTrue(service.contains("policies.update(")),
                () -> assertFalse(service.contains("ModelDefaultPolicyPort")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("@PostConstruct")),
                () -> assertFalse(service.contains("CREATE TABLE")),
                () -> assertFalse(Files.exists(projectRoot().resolve(TRIGGER
                        + "cn/lgs/orbisops/trigger/application/modelpolicy/OpsModelDefaultPolicyAdapter.java"))),
                () -> assertTrue(service.lines().count() < 40));
    }

    @Test
    void infrastructureMustOwnTheModelDefaultPolicyPortImplementation()
            throws IOException {
        String adapter = read(INFRASTRUCTURE
                + "cn/lgs/orbisops/infrastructure/adapter/repository/"
                + "JdbcModelDefaultPolicyAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains("implements ModelDefaultPolicyPort")),
                () -> assertTrue(adapter.contains("@Repository")),
                () -> assertTrue(adapter.contains("JdbcTemplate")),
                () -> assertTrue(adapter.contains("CREATE TABLE IF NOT EXISTS")),
                () -> assertTrue(adapter.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertFalse(adapter.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(adapter.lines().count() < 180));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
