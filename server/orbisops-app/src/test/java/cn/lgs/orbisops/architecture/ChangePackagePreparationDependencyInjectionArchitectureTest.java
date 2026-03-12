package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationDependencyInjectionArchitectureTest {

    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/OpsChangePackagePreparationService.java";
    private static final String TEST = "orbisops-app/src/test/java/"
            + "cn/lgs/orbisops/trigger/ops/change/OpsChangePackagePreparationServiceTest.java";

    @Test
    void optionalPreparationCollaboratorsMustBeConstructorBound() throws IOException {
        String service = read(SERVICE);
        String test = read(TEST);

        assertAll(
                () -> assertTrue(service.contains("@Autowired")),
                () -> assertTrue(service.contains("ObjectProvider<OpsRuntimeContextBundleAdapter> runtimeContextBundleServiceProvider")),
                () -> assertTrue(service.contains("ObjectProvider<OpsEvidenceStore> evidenceStoreProvider")),
                () -> assertFalse(service.contains("OpsToolExecutionService")),
                () -> assertFalse(service.contains("OpsProjectMcpRuntimeConfigService")),
                () -> assertFalse(service.contains("new OpsPreparationToolExecutionService(")),
                () -> assertTrue(service.contains("OWNING_PREPARE_AGENT_RUN_REQUIRED")),
                () -> assertTrue(service.contains("() -> available(runtimeContextBundleServiceProvider)")),
                () -> assertTrue(service.contains("() -> available(evidenceStoreProvider)")),
                () -> assertTrue(service.contains("private static <T> T available(")),
                () -> assertFalse(service.contains("@Autowired(required = false)")),
                () -> assertFalse(service.contains("private ObjectProvider<OpsRuntimeContextBundleAdapter>")),
                () -> assertFalse(service.contains("private ObjectProvider<OpsEvidenceStore>")),
                () -> assertFalse(test.contains("ReflectionTestUtils")),
                () -> assertFalse(test.contains("setField(service")),
                () -> assertTrue(test.contains("new OpsChangePackagePreparationService(")));
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
