package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageLandingRuntimeBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/changepackage/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/changepackage/";

    @Test
    void landingRuntimeMustExposeTypedAggregateDrivingResult() throws IOException {
        String port = read(APPLICATION + "ChangePackageLandingRuntimePort.java");
        String result = read(APPLICATION + "ChangePackageLandingRuntimeResult.java");
        String journal = read(APPLICATION + "ChangePackageLandingJournalPort.java");
        String fact = read(APPLICATION + "LandingOperationFact.java");
        String manager = read(APPLICATION + "ChangePackageLandingProcessManager.java");
        String adapter = read(TRIGGER + "OpsChangePackageLandingRuntimeAdapter.java");

        assertAll(
                () -> assertTrue(port.contains("ChangePackageLandingRuntimeResult execute(")),
                () -> assertFalse(port.contains("Map<String, Object> execute(")),
                () -> assertTrue(result.contains("ChangePackageStatus status")),
                () -> assertTrue(result.contains("String eventType")),
                () -> assertTrue(result.contains("boolean executedProductionAction")),
                () -> assertTrue(journal.contains("List<LandingOperationFact> operationFacts(")),
                () -> assertFalse(journal.contains("List<Map<String, Object>> operationFacts(")),
                () -> assertTrue(fact.contains("enum FactStatus")),
                () -> assertTrue(fact.contains("enum ExecutionStatus")),
                () -> assertTrue(manager.contains("runtimeResult.status()")),
                () -> assertTrue(manager.contains("runtimeResult.runStatus()")),
                () -> assertTrue(manager.contains("LandingOperationFact::unknown")),
                () -> assertTrue(manager.contains("LandingOperationFact::succeeded")),
                () -> assertFalse(manager.contains("result.get(\"eventType\")")),
                () -> assertFalse(manager.contains("row.get(\"fact_status\")")),
                () -> assertTrue(adapter.contains("ChangePackageStatus.require(")));
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
