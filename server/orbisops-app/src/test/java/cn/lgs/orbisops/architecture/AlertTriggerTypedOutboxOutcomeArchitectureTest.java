package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertTriggerTypedOutboxOutcomeArchitectureTest {

    @Test
    void alertOutboxProcessingMustKeepTypedOutcomeUntilHttpSerialization() throws IOException {
        String port = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/alert/AlertTriggerExecutionPort.java");
        String manager = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/alert/AlertTriggerProcessManager.java");
        String coordinator = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsAlertRunSubmissionCoordinator.java");
        String service = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsAlertTriggerService.java");
        String adapter = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/alert/OpsAlertTriggerExecutionAdapter.java");
        String job = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/job/OpsAlertTriggerOutboxJob.java");
        String controller = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/http/admin/OpsAlertTriggerAdminController.java");

        assertAll(
                () -> assertTrue(port.contains("AlertTriggerOutboxOutcome processPendingOutbox(int limit)")),
                () -> assertFalse(port.contains("Map<String, Object> processPendingOutbox")),
                () -> assertTrue(manager.contains("AlertTriggerOutboxOutcome processOutbox(int limit)")),
                () -> assertTrue(coordinator.contains("AlertTriggerOutboxOutcome processPending(")),
                () -> assertTrue(service.contains("AlertTriggerOutboxOutcome processPendingOutbox(")),
                () -> assertTrue(adapter.contains("AlertTriggerOutboxOutcome processPendingOutbox(int limit)")),
                () -> assertTrue(job.contains("AlertTriggerOutboxOutcome result")),
                () -> assertTrue(job.contains("result.hasActivity()")),
                () -> assertFalse(job.contains("result.get(")),
                () -> assertFalse(job.contains("private int number(")),
                () -> assertTrue(controller.contains("Response<AlertTriggerOutboxOutcome> processAlertTriggerOutbox(")));
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
