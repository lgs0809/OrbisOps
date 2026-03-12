package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationReflectionBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void observationRouterConsumesTypedReflectionDecisionWithoutOwningLlmProtocol() throws IOException {
        String executor = read(OPS + "OpsInvestigationExecutor.java");
        String loop = read(OPS + "OpsInvestigationLoopService.java");
        String routing = read(OPS + "OpsInvestigationObservationRoutingService.java");
        String reflection = read(OPS + "OpsInvestigationReflectionService.java");

        assertAll(
                () -> assertTrue(executor.contains("OpsInvestigationLoopService loopService")),
                () -> assertTrue(loop.contains("OpsInvestigationObservationRoutingService observationRoutingService")),
                () -> assertTrue(loop.contains("observationRoutingService.decide(")),
                () -> assertFalse(loop.contains("reflectionService.reflect(")),
                () -> assertTrue(routing.contains("OpsInvestigationReflectionService reflectionService")),
                () -> assertTrue(routing.contains("reflectionService.reflect(")),
                () -> assertTrue(routing.contains("OpsInvestigationReflectionService.Decision reflection")),
                () -> assertTrue(routing.contains("reflection.tasks().forEach")),
                () -> assertTrue(routing.contains("shouldStopAfterKnowledgeObservation(")),
                () -> assertFalse(routing.contains("chatJsonObject(")),
                () -> assertFalse(routing.contains("OpsLlmJsonValidator.validateMainReflection")),
                () -> assertFalse(routing.contains("JSONObject")),
                () -> assertFalse(routing.contains("JSONArray")),
                () -> assertFalse(routing.contains("buildReflectionPrompt(")),
                () -> assertFalse(routing.contains("你是运维 multi-agent 系统的主 Agent")),
                () -> assertFalse(routing.contains("@Service")),
                () -> assertFalse(routing.contains("org.springframework")),
                () -> assertTrue(reflection.contains("chatJsonObject(")),
                () -> assertTrue(reflection.contains("OpsLlmJsonValidator.validateMainReflection")),
                () -> assertTrue(reflection.contains("SYSTEM_PROMPT")),
                () -> assertTrue(reflection.contains("buildPrompt(")),
                () -> assertTrue(reflection.contains("record Decision(")),
                () -> assertTrue(reflection.contains("registeredSources")),
                () -> assertTrue(reflection.contains("executedSources")),
                () -> assertTrue(reflection.contains("queuedSources")),
                () -> assertFalse(reflection.contains("@Service")),
                () -> assertFalse(reflection.contains("ObjectProvider")),
                () -> assertFalse(reflection.contains("org.springframework")));
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
