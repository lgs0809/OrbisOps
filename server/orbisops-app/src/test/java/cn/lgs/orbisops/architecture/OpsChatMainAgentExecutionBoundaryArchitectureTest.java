package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChatMainAgentExecutionBoundaryArchitectureTest {

    @Test
    void normalChatMustUseOneModelSelectedAgentAction() throws IOException {
        String facade = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatMainAgentExecutionFacade.java");
        String chat = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatApplicationService.java");
        String actionType = read("orbisops-application/src/main/java/cn/lgs/orbisops/application/agent/OpsMainAgentActionType.java");

        assertAll(
                () -> assertTrue(actionType.contains("AGENT")),
                () -> assertTrue(facade.contains("OpsMainAgentActionType.AGENT")),
                () -> assertTrue(facade.contains("MODEL_TOOL_SELECTION")),
                () -> assertTrue(facade.contains("mainAgentCoordinator.execute")),
                () -> assertFalse(facade.contains("fixedActionService.supports")),
                () -> assertFalse(facade.contains("projectHarness(intent")),
                () -> assertTrue(chat.contains("mainAgentExecutionFacade.execute")),
                () -> assertFalse(chat.contains("requiresCompositeExecution")),
                () -> assertFalse(chat.contains("intentSequenceExecutionFacade")));
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
