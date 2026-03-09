package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLlmJsonCallOrchestrationBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void llmClientDelegatesInitialSkillRetryRepairAndInvalidEventOrdering() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String orchestrator = read(OPS + "OpsLlmJsonCallOrchestrator.java");

        assertAll(
                () -> assertTrue(client.contains("OpsLlmJsonCallOrchestrator jsonCallOrchestrator")),
                () -> assertTrue(client.contains("jsonCallOrchestrator.execute(")),
                () -> assertTrue(client.contains("new OpsLlmJsonCallOrchestrator.Input(")),
                () -> assertTrue(client.contains("this::callContent")),
                () -> assertFalse(client.contains("jsonProtocol.parse(")),
                () -> assertFalse(client.contains("jsonProtocol.repairSystemPrompt(")),
                () -> assertFalse(client.contains("jsonProtocol.repairUserPrompt(")),
                () -> assertFalse(client.contains("shouldRetryWithEagerSkillContext(")),
                () -> assertFalse(client.contains("agentName + \"-skill-context-retry\"")),
                () -> assertFalse(client.contains("agentName + \"-json-repair\"")),
                () -> assertFalse(client.contains("observabilityService.modelJsonInvalid(")),
                () -> assertTrue(client.lines().count() <= 270),
                () -> assertTrue(orchestrator.contains("String initialContent = caller.call(")),
                () -> assertTrue(orchestrator.contains("jsonProtocol.parse(initialContent")),
                () -> assertTrue(orchestrator.contains("shouldRetryWithEagerSkillContext(")),
                () -> assertTrue(orchestrator.contains("-skill-context-retry")),
                () -> assertTrue(orchestrator.contains("-json-repair")),
                () -> assertTrue(orchestrator.contains("jsonProtocol.repairSystemPrompt()")),
                () -> assertTrue(orchestrator.contains("jsonProtocol.repairUserPrompt(")),
                () -> assertTrue(orchestrator.contains("initialContent,")),
                () -> assertTrue(orchestrator.contains("recordInvalid(")),
                () -> assertTrue(orchestrator.contains("List.of(),")),
                () -> assertFalse(orchestrator.contains("@Service")),
                () -> assertFalse(orchestrator.contains("@Value")),
                () -> assertFalse(orchestrator.contains("ApplicationContext")),
                () -> assertFalse(orchestrator.contains("ObjectProvider")),
                () -> assertTrue(orchestrator.lines().count() <= 220));
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
