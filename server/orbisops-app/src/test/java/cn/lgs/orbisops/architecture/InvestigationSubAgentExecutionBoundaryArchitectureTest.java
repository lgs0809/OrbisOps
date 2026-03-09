package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationSubAgentExecutionBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void unifiedLoopDelegatesReliableSubAgentExecutionToPlainBoundary() throws IOException {
        String executor = read(OPS + "OpsInvestigationExecutor.java");
        String loop = read(OPS + "OpsInvestigationLoopService.java");
        String execution = read(OPS + "OpsInvestigationSubAgentExecutionService.java");

        assertAll(
                () -> assertTrue(executor.contains("OpsInvestigationSubAgentExecutionService subAgentExecutionService")),
                () -> assertTrue(executor.contains("OpsInvestigationLoopService loopService")),
                () -> assertTrue(executor.contains("subAgentExecutionService.executeOne(")),
                () -> assertFalse(executor.contains("subAgentExecutionService.executeBatch(")),
                () -> assertTrue(loop.contains("OpsInvestigationSubAgentExecutionService subAgentExecutionService")),
                () -> assertTrue(loop.contains("subAgentExecutionService.executeBatch(")),
                () -> assertTrue(loop.contains("subAgentExecutionService.executeOne(")),
                () -> assertTrue(loop.contains("subAgentExecutionService.assertNotCanceled(")),
                () -> assertTrue(loop.contains("subAgentExecutionService.registeredSources()")),
                () -> assertFalse(executor.contains("CompletableFuture")),
                () -> assertFalse(loop.contains("CompletableFuture")),
                () -> assertFalse(executor.contains("OpsLlmTraceContext")),
                () -> assertFalse(loop.contains("OpsLlmTraceContext")),
                () -> assertFalse(executor.contains("forkResponse(")),
                () -> assertFalse(loop.contains("forkResponse(")),
                () -> assertFalse(executor.contains("mergeTaskExecution(")),
                () -> assertFalse(loop.contains("mergeTaskExecution(")),
                () -> assertFalse(executor.contains("responseFragment")),
                () -> assertFalse(loop.contains("responseFragment")),
                () -> assertFalse(executor.contains("blockedResult(")),
                () -> assertFalse(loop.contains("blockedResult(")),
                () -> assertFalse(executor.contains("taskTimeoutSeconds(")),
                () -> assertFalse(loop.contains("taskTimeoutSeconds(")),
                () -> assertFalse(executor.contains("trimResult(")),
                () -> assertFalse(loop.contains("trimResult(")),
                () -> assertFalse(executor.contains(".investigate(")),
                () -> assertFalse(loop.contains(".investigate(")),
                () -> assertFalse(executor.contains("Map<String, OpsSubAgent>")),
                () -> assertFalse(loop.contains("Map<String, OpsSubAgent>")),
                () -> assertTrue(execution.contains("FutureTask<IsolatedExecution>")),
                () -> assertTrue(execution.contains("executor.execute(future)")),
                () -> assertTrue(execution.contains("OpsLlmTraceContext.wrap(")),
                () -> assertTrue(execution.contains("future().get(taskTimeoutSeconds(request), TimeUnit.SECONDS)")),
                () -> assertTrue(execution.contains("private OpsAnalysisResponseDTO fork(")),
                () -> assertTrue(execution.contains("private void merge(")),
                () -> assertTrue(execution.contains("private OpsAnalysisResponseDTO.InvestigationResultDTO trimResult(")),
                () -> assertTrue(execution.contains("Set<String> registeredSources()")),
                () -> assertTrue(execution.contains("void assertNotCanceled(")),
                () -> assertTrue(execution.contains("subAgent.investigate(")),
                () -> assertFalse(execution.contains("@Service")),
                () -> assertFalse(execution.contains("@Value")),
                () -> assertFalse(execution.contains("OpsInvestigationReflectionService")),
                () -> assertFalse(execution.contains("InvestigationFollowUpPolicy")));
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
