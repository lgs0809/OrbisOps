package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertAnalysisRequestBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void durableSubmissionOwnsVersionBindingRequestMaterializationAndOutboxMapping() throws IOException {
        String trigger = read(OPS + "OpsAlertTriggerService.java");
        String submissions = read(OPS + "OpsAlertRunSubmissionCoordinator.java");
        String factory = read(OPS + "OpsAlertAnalysisRequestFactory.java");

        assertAll(
                () -> assertTrue(trigger.contains(
                        "OpsAlertRunSubmissionCoordinator submissions")),
                () -> assertFalse(trigger.contains(
                        "OpsAlertAnalysisRequestFactory requestFactory")),
                () -> assertFalse(trigger.contains("requestFactory.build(")),
                () -> assertFalse(trigger.contains("private OpsAgentDefinitionQueryGateway")),
                () -> assertFalse(trigger.contains("private OpsAgentRunRequestDTO buildRequest(")),
                () -> assertFalse(trigger.contains("alertOutbox.enqueue(")),
                () -> assertFalse(trigger.contains("ops.runtime.OpsAgentDefinition")),
                () -> assertFalse(trigger.contains("OpsAgentDefinition agent")),
                () -> assertFalse(trigger.contains("JSON.toJSONString(agent)")),
                () -> assertFalse(trigger.contains("resolveRuleAgent(")),
                () -> assertFalse(trigger.contains("agentBindingMode(")),
                () -> assertFalse(trigger.contains("renderQuestion(")),
                () -> assertFalse(trigger.contains("OpsAgentRunRequestDTO.builder()")),
                () -> assertFalse(trigger.contains("ALERT_AGENT_VERSION_INCOMPLETE")),
                () -> assertFalse(trigger.contains("ALERT_AGENT_DEFINITION_HASH_MISMATCH")),
                () -> assertTrue(trigger.lines().count() <= 260),
                () -> assertTrue(submissions.contains(
                        "OpsAlertAnalysisRequestFactory requestFactory")),
                () -> assertTrue(submissions.contains(
                        "OpsAgentDefinitionQueryGateway agentDefinitions")),
                () -> assertTrue(submissions.contains("requestFactory.build(")),
                () -> assertTrue(submissions.contains("alertOutbox.enqueue(")),
                () -> assertTrue(submissions.contains("mapper.draft(")),
                () -> assertTrue(submissions.lines().count() <= 150),
                () -> assertTrue(factory.contains("OpsAgentRunRequestDTO build(")),
                () -> assertTrue(factory.contains("OpsAgentDefinitionQueryGateway")),
                () -> assertTrue(factory.contains("OpsAgentRunRequestDTO.builder()")),
                () -> assertTrue(factory.contains("resolveForProject(")),
                () -> assertTrue(factory.contains("LATEST_PUBLISHED")),
                () -> assertTrue(factory.contains("PINNED_VERSION")),
                () -> assertTrue(factory.contains("ALERT_AGENT_REGISTRY_UNAVAILABLE")),
                () -> assertTrue(factory.contains("ALERT_AGENT_VERSION_INCOMPLETE")),
                () -> assertTrue(factory.contains("ALERT_AGENT_DEFINITION_HASH_MISMATCH")),
                () -> assertTrue(factory.contains("JSON.toJSONString(agent)")),
                () -> assertTrue(factory.contains("${occurrenceCount}")),
                () -> assertTrue(factory.contains(".triggerSource(triggerSource)")),
                () -> assertTrue(factory.contains(".triggerEventId(alert.fingerprint())")),
                () -> assertFalse(factory.contains("@Service")),
                () -> assertFalse(factory.contains("@Component")),
                () -> assertFalse(factory.contains("@Value")),
                () -> assertFalse(factory.contains("@Autowired")),
                () -> assertFalse(factory.contains("AlertAggregationApplicationService")),
                () -> assertFalse(factory.contains("AlertOutboxApplicationService")),
                () -> assertFalse(factory.contains("AlertEventApplicationService")),
                () -> assertFalse(factory.contains("OpsAnalysisRunService")),
                () -> assertTrue(factory.lines().count() <= 170));
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
