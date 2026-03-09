package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationPlanningProtocolBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void plannerDelegatesLlmProtocolAndKeepsOnlyFallbackAndGraphApplicationFlow() throws IOException {
        String planner = read(OPS + "OpsMainAgentPlanner.java");
        String protocol = read(OPS + "OpsMainAgentPlanningProtocolService.java");
        String mapper = read(OPS + "OpsMainAgentPlanningJsonMapper.java");

        assertAll(
                () -> assertTrue(planner.contains("OpsMainAgentPlanningProtocolService planningProtocolService")),
                () -> assertTrue(planner.contains("planningProtocolService.plan(")),
                () -> assertTrue(planner.contains("planningProtocolService.replan(")),
                () -> assertFalse(planner.contains("chatJsonObject(")),
                () -> assertFalse(planner.contains("OpsLlmJsonValidator.validatePlanner")),
                () -> assertFalse(planner.contains("OpsLlmJsonValidator.validateReplanner")),
                () -> assertFalse(planner.contains("buildPlannerPrompt(")),
                () -> assertFalse(planner.contains("buildReplanPrompt(")),
                () -> assertFalse(planner.contains("parsePlan(")),
                () -> assertFalse(planner.contains("parseTasks(")),
                () -> assertFalse(planner.contains("你是运维 multi-agent 系统的主 Agent，采用 Plan-and-Execute")),
                () -> assertTrue(planner.contains("graphRoutingService.filterPlan(")),
                () -> assertTrue(planner.contains("graphRoutingService.emptyPlan(")),
                () -> assertTrue(planner.lines().count() <= 420),
                () -> assertTrue(protocol.contains("chatJsonObjectWithEagerSkillContext(")),
                () -> assertTrue(protocol.contains("OpsLlmJsonValidator.validatePlanner")),
                () -> assertTrue(protocol.contains("OpsLlmJsonValidator.validateReplanner")),
                () -> assertTrue(protocol.contains("PLANNER_SYSTEM_PROMPT")),
                () -> assertTrue(protocol.contains("REPLANNER_SYSTEM_PROMPT")),
                () -> assertTrue(protocol.contains("Attempt(Status status")),
                () -> assertTrue(protocol.contains("Status.NO_JSON")),
                () -> assertTrue(protocol.contains("Status.INVALID_SCHEMA")),
                () -> assertTrue(protocol.contains("OpsMainAgentPlanningJsonMapper jsonMapper")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertFalse(protocol.contains("@Value")),
                () -> assertFalse(protocol.contains("org.springframework")),
                () -> assertTrue(protocol.lines().count() <= 350),
                () -> assertTrue(mapper.contains("OpsInvestigationPlanDTO toPlan(")),
                () -> assertTrue(mapper.contains("JSONArray")),
                () -> assertTrue(mapper.contains("JSONObject")),
                () -> assertFalse(mapper.contains("chatJsonObject(")),
                () -> assertFalse(mapper.contains("OpsLlmJsonValidator")),
                () -> assertFalse(mapper.contains("@Service")),
                () -> assertFalse(mapper.contains("org.springframework")),
                () -> assertTrue(mapper.lines().count() <= 150));
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
