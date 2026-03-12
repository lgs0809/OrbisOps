package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLandingRecoveryBoundaryArchitectureTest {

    private static final String CHANGE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/changepackage/";

    @Test
    void recoveryFacadeDelegatesTypedSettingsCatalogContextPostCheckAndOutcome() throws IOException {
        String service = read(CHANGE + "OpsLandingOperationRecoveryService.java");
        String settings = read(CHANGE + "OpsLandingRecoverySettings.java");
        String catalog = read(CHANGE + "OpsLandingOperationExecutorCatalog.java");
        String contextFactory = read(CHANGE + "OpsLandingRecoveryContextFactory.java");
        String verifier = read(CHANGE + "OpsLandingRecoveryPostCheckVerifier.java");
        String reporter = read(CHANGE + "OpsLandingRecoveryOutcomeReporter.java");
        String configuration = read(APPLICATION + "OpsLandingRecoveryConfiguration.java");

        assertAll(
                () -> assertTrue(service.contains("OpsLandingRecoverySettings settings")),
                () -> assertTrue(service.contains("OpsLandingOperationExecutorCatalog executors")),
                () -> assertTrue(service.contains("OpsLandingRecoveryContextFactory contextFactory")),
                () -> assertTrue(service.contains("OpsLandingRecoveryPostCheckVerifier postCheckVerifier")),
                () -> assertTrue(service.contains("OpsLandingRecoveryOutcomeReporter outcomeReporter")),
                () -> assertTrue(service.contains("@Autowired")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("UUID.randomUUID()")),
                () -> assertFalse(service.contains("new ChangePackageLandingOperationSafetyPolicy")),
                () -> assertFalse(service.contains("auditService.recordRuntimeEvent(")),
                () -> assertFalse(service.contains("journal.completeReconciliation(")),
                () -> assertTrue(service.lines().count() <= 210),
                () -> assertTrue(settings.contains("public record OpsLandingRecoverySettings(")),
                () -> assertTrue(catalog.contains("orderedStream()")),
                () -> assertTrue(contextFactory.contains("UUID.randomUUID()")),
                () -> assertTrue(verifier.contains("ChangePackageLandingOperationSafetyPolicy")),
                () -> assertTrue(verifier.contains("executor.readCurrentState(")),
                () -> assertTrue(reporter.contains("journal.completeReconciliation(")),
                () -> assertTrue(reporter.contains("journal.leaveReconciliationUnknown(")),
                () -> assertTrue(reporter.contains("landingProcessManager.reconcile(")),
                () -> assertTrue(configuration.contains("orbisops.approved-landing.recovery.enabled")),
                () -> assertTrue(configuration.contains("orbisops.approved-landing.recovery.batch-size")),
                () -> assertTrue(configuration.contains("reconciliation-timeout-seconds")));
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
