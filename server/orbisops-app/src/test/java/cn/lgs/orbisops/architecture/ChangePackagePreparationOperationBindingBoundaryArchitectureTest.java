package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationOperationBindingBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void plainFactoryOwnsOperationCompatibilityNormalizationAndBindings() throws IOException {
        String factory = read(TRIGGER + "OpsPreparationOperationBindingFactory.java");

        assertAll(
                () -> assertTrue(factory.contains("final class OpsPreparationOperationBindingFactory")),
                () -> assertTrue(factory.contains("OperationBundle create(")),
                () -> assertTrue(factory.contains("normalizeSteps(")),
                () -> assertTrue(factory.contains("toolBindings(")),
                () -> assertTrue(factory.contains("remoteToolName")),
                () -> assertTrue(factory.contains("targetResourceScope")),
                () -> assertTrue(factory.contains("target_environment")),
                () -> assertTrue(factory.contains("ChangePackagePreparationOperation.normalizeEffectType(")),
                () -> assertTrue(factory.contains("schemaBound(")),
                () -> assertTrue(factory.contains("permissionGranted")),
                () -> assertTrue(factory.contains("record OperationBundle")),
                () -> assertTrue(factory.contains("Collections.unmodifiableMap")),
                () -> assertFalse(factory.contains("org.springframework")),
                () -> assertFalse(factory.contains("@Service")),
                () -> assertFalse(factory.contains("ObjectProvider")),
                () -> assertFalse(factory.contains("interface OpsPreparationOperationBinding")));
    }

    @Test
    void preparationServiceConsumesFrozenBundleWithoutDuplicatingInputProtocol() throws IOException {
        String preparation = read(TRIGGER + "OpsChangePackagePreparationService.java");

        assertAll(
                () -> assertTrue(preparation.contains("OpsPreparationOperationBindingFactory PREPARATION_OPERATION_BINDING_FACTORY")),
                () -> assertTrue(preparation.contains("PREPARATION_OPERATION_BINDING_FACTORY.create(")),
                () -> assertTrue(preparation.contains("operationBundle.operations()")),
                () -> assertTrue(preparation.contains("operationBundle.toolBindings()")),
                () -> assertFalse(preparation.contains("normalizeMcpSteps(")),
                () -> assertFalse(preparation.contains("private List<Map<String, Object>> toolBindings(")),
                () -> assertFalse(preparation.contains("remoteToolName")),
                () -> assertFalse(preparation.contains("targetResourceScope")),
                () -> assertFalse(preparation.contains("permissionGranted")));
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
