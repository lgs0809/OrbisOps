package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedIntentGovernedRuntimeArchitectureTest {

    @Test
    void runtimeAuthorityMustComeFromServerContextNotBusinessIntent() throws IOException {
        String preparation = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/OpsWorkSessionPreparationCoordinator.java");
        String authority = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/OpsAgentRunExecutionContextFactory.java");
        String harness = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/OpsExecutionHarness.java");

        assertAll(
                () -> assertFalse(preparation.contains("chatEntryCoordinator.route")),
                () -> assertFalse(preparation.contains("OpsIntentDecision")),
                () -> assertTrue(preparation.contains("bindServerContext(safeRequest, definition)")),
                () -> assertTrue(authority.contains("preApprovalStage(triggerSource)")),
                () -> assertTrue(authority.contains("case INSPECTION, SCHEDULE, ALERT")),
                () -> assertTrue(authority.contains("AgentExecutionStage.PREPARE")),
                () -> assertTrue(harness.contains("AgentRunExecutionContext")),
                () -> assertFalse(harness.contains("metadata().get(\"executionHarness\")")));
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
