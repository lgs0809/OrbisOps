package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsChatIntentSequenceExecutionBoundaryArchitectureTest {

    @Test
    void chatMustNotExecuteModelTasksThroughIntentSequencePlanner() throws IOException {
        String chat = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatApplicationService.java");
        String mainAgent = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/ops/OpsChatMainAgentExecutionFacade.java");

        assertFalse(chat.contains("OpsChatIntentSequenceExecutionFacade"));
        assertFalse(chat.contains("OpsIntentExecutionPlanner"));
        assertFalse(chat.contains("requiresCompositeExecution"));
        assertFalse(mainAgent.contains("CONTROL_PLANE_ACTION"));
        assertFalse(mainAgent.contains("PRE_APPROVAL_WORK_SESSION"));
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
