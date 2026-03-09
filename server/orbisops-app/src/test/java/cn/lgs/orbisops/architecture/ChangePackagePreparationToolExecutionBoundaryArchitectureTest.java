package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationToolExecutionBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void preparationServiceCompilesPackageWithoutStartingSecondToolChain() throws IOException {
        String preparation = read(TRIGGER + "OpsChangePackagePreparationService.java");

        assertAll(
                () -> assertFalse(preparation.contains("OpsPreparationToolExecutionService preparationToolExecutionService")),
                () -> assertFalse(preparation.contains("preparationToolExecutionService.execute(")),
                () -> assertTrue(preparation.contains("verifiedPrepareResult(request)")),
                () -> assertTrue(preparation.contains("OWNING_PREPARE_AGENT_RUN_REQUIRED")),
                () -> assertTrue(preparation.contains("PREPARATION_OPERATION_BINDING_FACTORY.create(")),
                () -> assertFalse(preparation.contains("activePrepareResults(")),
                () -> assertFalse(preparation.contains("executePrepareStep(")),
                () -> assertFalse(preparation.contains("stepAggregate(")),
                () -> assertFalse(preparation.contains("prepareEffectAllowed(")),
                () -> assertFalse(preparation.contains("targetMutation(")),
                () -> assertFalse(preparation.contains("statusFromSecurity(")),
                () -> assertFalse(preparation.contains("private String normalizeEffectType(")),
                () -> assertFalse(preparation.contains("executeMcp(")),
                () -> assertFalse(preparation.contains("OpsMcpServerConfig")));
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
