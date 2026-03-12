package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAlertTriggerBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String ALERT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/alert/";

    @Test
    void facadeUsesTypedSettingsAndNarrowAlertCollaborators() throws IOException {
        String facade = read(OPS + "OpsAlertTriggerService.java");
        String settings = read(OPS + "OpsAlertTriggerSettings.java");
        String configuration = read(ALERT + "OpsAlertTriggerConfiguration.java");
        String catalog = read(OPS + "OpsAlertRuleCatalog.java");
        String recorder = read(OPS + "OpsAlertEventRecorder.java");
        String submissions = read(OPS + "OpsAlertRunSubmissionCoordinator.java");
        String summaries = read(OPS + "OpsAlertSummaryCoordinator.java");
        String accumulator = read(OPS + "OpsAlertWebhookResultAccumulator.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsAlertTriggerSettings settings")),
                () -> assertTrue(facade.contains("OpsAlertRuleCatalog rules")),
                () -> assertTrue(facade.contains("OpsAlertEventRecorder events")),
                () -> assertTrue(facade.contains("OpsAlertRunSubmissionCoordinator submissions")),
                () -> assertTrue(facade.contains("OpsAlertSummaryCoordinator summaries")),
                () -> assertTrue(facade.contains("OpsAlertWebhookResultAccumulator result")),
                () -> assertTrue(facade.contains("@Autowired")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("ReflectionTestUtils")),
                () -> assertFalse(facade.contains("private OpsAgentDefinitionQueryGateway")),
                () -> assertFalse(facade.contains("private final OpsAlertRuleMapper")),
                () -> assertFalse(facade.contains("private final OpsAlertEventMapper")),
                () -> assertFalse(facade.contains("private final OpsAlertOutboxMapper")),
                () -> assertFalse(facade.contains("private int enqueueDueSummaries(")),
                () -> assertFalse(facade.contains("private void enqueueOutbox(")),
                () -> assertFalse(facade.contains("private OpsAlertTriggerEvent insertEvent(")),
                () -> assertTrue(facade.lines().count() <= 260),
                () -> assertTrue(settings.contains("public record OpsAlertTriggerSettings(")),
                () -> assertTrue(settings.contains("public static OpsAlertTriggerSettings defaults()")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(configuration.contains("@Bean")),
                () -> assertTrue(configuration.contains("orbisops.alert-triggers.signature-max-skew-seconds")),
                () -> assertTrue(configuration.contains("orbisops.alert-triggers.outbox.max-attempts")),
                () -> assertTrue(configuration.contains("orbisops.alert-triggers.project-queue.max-running")),
                () -> assertTrue(catalog.contains("activeForSource(")),
                () -> assertTrue(recorder.contains("alertEvents.append(mapper.draft(")),
                () -> assertTrue(submissions.contains("alertOutbox.enqueue(")),
                () -> assertTrue(submissions.contains("alertOutbox.dispatchIfCapacity(")),
                () -> assertTrue(summaries.contains("claimDueSummaries(")),
                () -> assertTrue(summaries.contains("releaseSummaryClaim(")),
                () -> assertTrue(accumulator.contains("OpsAlertWebhookResult.builder()")));
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
