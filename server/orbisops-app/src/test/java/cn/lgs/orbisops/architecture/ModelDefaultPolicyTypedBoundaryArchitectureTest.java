package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDefaultPolicyTypedBoundaryArchitectureTest {

    @Test
    void defaultModelPolicyPersistenceMustRemainTyped() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/modelpolicy/ModelDefaultPolicyPort.java");
        String service = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/modelpolicy/ModelDefaultPolicyApplicationService.java");
        String adapter = read("orbisops-infrastructure/src/main/java/"
                + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcModelDefaultPolicyAdapter.java");
        String legacy = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsModelDefaultPolicyService.java");

        assertAll(
                () -> assertTrue(port.contains("Optional<ModelDefaultPolicySnapshot> find")),
                () -> assertTrue(port.contains("ModelDefaultPolicySnapshot save(ModelDefaultPolicy policy)")),
                () -> assertFalse(port.contains("Map<String, Object>")),
                () -> assertTrue(service.contains("policyPort.find(")),
                () -> assertTrue(service.contains("policyPort.save(policy)")),
                () -> assertFalse(service.contains("policyPort.update(")),
                () -> assertTrue(adapter.contains("new ModelDefaultPolicy(")),
                () -> assertTrue(adapter.contains("ModelPolicyStatus.require(")),
                () -> assertFalse(adapter.contains("request.get(")),
                () -> assertTrue(legacy.contains("ModelDefaultPolicyApplicationService")),
                () -> assertFalse(legacy.contains("ModelDefaultPolicyPort")));
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
