package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChatIntentSequenceBoundaryArchitectureTest {

    @Test
    void oneUserTurnMustRemainOneMainAgentRun() throws IOException {
        String chat = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatApplicationService.java");
        String facade = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatMainAgentExecutionFacade.java");

        assertAll(
                () -> assertTrue(chat.contains("executeThroughMainAgent")),
                () -> assertTrue(facade.contains("routing\", \"MODEL_TOOL_SELECTION")),
                () -> assertFalse(chat.contains("intentSequence")),
                () -> assertFalse(chat.contains("completedIntentStepCount")),
                () -> assertFalse(chat.contains("intentSteps")),
                () -> assertFalse(facade.contains("fixedActionService.supports")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
