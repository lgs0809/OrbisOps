package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWorkSessionRecoveryBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/worksession/";

    @Test
    void recoveryWorkerDelegatesTypedBatchAndDecisionReporting() throws IOException {
        String worker = read(RUNTIME + "OpsWorkSessionRecoveryWorker.java");
        String settings = read(RUNTIME + "OpsWorkSessionRecoverySettings.java");
        String reporter = read(RUNTIME + "OpsWorkSessionRecoveryDecisionReporter.java");
        String configuration = read(APPLICATION + "OpsWorkSessionRecoveryConfiguration.java");

        assertAll(
                () -> assertTrue(worker.contains("OpsWorkSessionRecoverySettings settings")),
                () -> assertTrue(worker.contains("OpsWorkSessionRecoveryDecisionReporter reporter")),
                () -> assertTrue(worker.contains("recoverAndExecuteExpiredLeases(settings.batchSize())")),
                () -> assertTrue(worker.contains("reporter.report(execution)")),
                () -> assertTrue(worker.contains("public void recoverExpiredRuns()")),
                () -> assertTrue(worker.contains("@Autowired")),
                () -> assertFalse(worker.contains("@Value")),
                () -> assertFalse(worker.contains("publishRunEvent(")),
                () -> assertFalse(worker.contains("recordRuntimeEvent(")),
                () -> assertTrue(worker.lines().count() <= 75),
                () -> assertTrue(settings.contains("public record OpsWorkSessionRecoverySettings(")),
                () -> assertTrue(reporter.contains("WORK_SESSION_RECOVERABLE")),
                () -> assertTrue(reporter.contains("WORK_SESSION_RECOVERY_REVIEW_REQUIRED")),
                () -> assertTrue(reporter.contains("graphEventService.publishRunEvent(")),
                () -> assertTrue(reporter.contains("auditService.recordRuntimeEvent(")),
                () -> assertFalse(reporter.contains("@Component")),
                () -> assertTrue(configuration.contains("orbisops.work-session.recovery.enabled")),
                () -> assertTrue(configuration.contains("orbisops.work-session.recovery.batch-size")));
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
