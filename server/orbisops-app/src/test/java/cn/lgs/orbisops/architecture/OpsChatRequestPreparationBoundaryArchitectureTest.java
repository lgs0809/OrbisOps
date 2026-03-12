package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChatRequestPreparationBoundaryArchitectureTest {

    @Test
    void chatPreparationMustOnlyBindServerOwnedRequestContext() throws IOException {
        String facade = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/chat/OpsChatRequestPreparationFacade.java");
        String chat = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatApplicationService.java");

        assertAll(
                () -> assertTrue(facade.contains("ensureRunId")),
                () -> assertTrue(facade.contains("applyProjectAgent")),
                () -> assertTrue(facade.contains("sessions.assertWrite")),
                () -> assertTrue(facade.contains("projects.defaultAgentId")),
                () -> assertFalse(facade.contains("intentRouter.route")),
                () -> assertFalse(facade.contains("memoryApplicationService.apply")),
                () -> assertFalse(facade.contains("ChatIntentPreparationUseCase")),
                () -> assertFalse(facade.contains("_platformIntentDecision")),
                () -> assertFalse(facade.contains("_explicitMemoryApplied")),
                () -> assertTrue(chat.contains("requestPreparationFacade.prepareOrCreate")),
                () -> assertTrue(chat.contains("requestPreparationFacade.prepareRequired")),
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
