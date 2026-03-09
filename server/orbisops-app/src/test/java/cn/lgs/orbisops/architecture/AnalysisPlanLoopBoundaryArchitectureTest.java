package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisPlanLoopBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void coordinatorMustOwnOnlyMainLoopOrderingAndStableFacadeMethods()
            throws IOException {
        String coordinator = read(RUNTIME + "OpsAnalysisPlanLoopCoordinator.java");

        assertAll(
                () -> assertTrue(coordinator.contains(
                        "private final OpsAnalysisPlanLifecycle planLifecycle;")),
                () -> assertTrue(coordinator.contains(
                        "private final OpsAnalysisImmediateFollowUpCoordinator immediateFollowUpCoordinator;")),
                () -> assertTrue(coordinator.contains(
                        "private final OpsAnalysisReplanTaskRunner replanTaskRunner;")),
                () -> assertTrue(coordinator.contains("planLifecycle.ensurePlan(")),
                () -> assertTrue(coordinator.contains("immediateFollowUpCoordinator.trigger(")),
                () -> assertTrue(coordinator.contains("replanTaskRunner.execute(")),
                () -> assertTrue(coordinator.contains("replanTaskRunner.waitForInitialSubAgents(")),
                () -> assertFalse(coordinator.contains("CompletableFuture")),
                () -> assertFalse(coordinator.contains("TimeoutException")),
                () -> assertFalse(coordinator.contains("ExecutionException")),
                () -> assertFalse(coordinator.contains("OpsLlmTraceContext")),
                () -> assertFalse(coordinator.contains("executeGraphImmediateFollowUps(")),
                () -> assertFalse(coordinator.contains("canonicalUnexecutedTasks(")),
                () -> assertEquals(1, occurrences(
                        coordinator, "OpsAnalysisPlanLoopCoordinator(")),
                () -> assertTrue(coordinator.lines().count() < 230));
    }

    @Test
    void planLifecycleMustOwnPlannerTransitionsOnly() throws IOException {
        String lifecycle = read(RUNTIME + "OpsAnalysisPlanLifecycle.java");

        assertAll(
                () -> assertTrue(lifecycle.contains("OpsMainAgentPlanner")),
                () -> assertTrue(lifecycle.contains("ensurePlan(")),
                () -> assertTrue(lifecycle.contains("planner.plan(")),
                () -> assertTrue(lifecycle.contains("planner.replan(")),
                () -> assertFalse(lifecycle.contains("OpsInvestigationExecutor")),
                () -> assertFalse(lifecycle.contains("CompletableFuture")),
                () -> assertFalse(lifecycle.contains("OpsAnalysisRuntimeStateManager")),
                () -> assertTrue(lifecycle.lines().count() < 70));
    }

    @Test
    void immediateFollowUpCoordinatorMustOwnSourceScopedMerge() throws IOException {
        String immediate = read(
                RUNTIME + "OpsAnalysisImmediateFollowUpCoordinator.java");

        assertAll(
                () -> assertTrue(immediate.contains("executeGraphImmediateFollowUps(")),
                () -> assertTrue(immediate.contains("immediateFollowUpSources.add(")),
                () -> assertTrue(immediate.contains("synchronized (initialResults)")),
                () -> assertTrue(immediate.contains("recordFollowUpSteps(")),
                () -> assertTrue(immediate.contains("prefixNotes(")),
                () -> assertFalse(immediate.contains("planner.replan(")),
                () -> assertFalse(immediate.contains("CompletableFuture")),
                () -> assertFalse(immediate.contains("OpsLlmTraceContext")),
                () -> assertTrue(immediate.lines().count() < 120));
    }

    @Test
    void replanRunnerMustOwnConcurrencyTimeoutTraceAndBlockedProjection()
            throws IOException {
        String runner = read(RUNTIME + "OpsAnalysisReplanTaskRunner.java");

        assertAll(
                () -> assertTrue(runner.contains("canonicalUnexecutedTasks(")),
                () -> assertTrue(runner.contains("CompletableFuture.supplyAsync(")),
                () -> assertTrue(runner.contains("OpsLlmTraceContext.wrap(")),
                () -> assertTrue(runner.contains("future.get(")),
                () -> assertTrue(runner.contains("TimeoutException")),
                () -> assertTrue(runner.contains("ExecutionException")),
                () -> assertTrue(runner.contains("status(\"BLOCKED\")")),
                () -> assertTrue(runner.contains("waitForInitialSubAgents(")),
                () -> assertFalse(runner.contains("planner.replan(")),
                () -> assertFalse(runner.contains("executeGraphImmediateFollowUps(")),
                () -> assertTrue(runner.lines().count() < 280));
    }

    @Test
    void planLoopBoundariesMustRemainFreeOfDynamicInjection() throws IOException {
        String combined = read(RUNTIME + "OpsAnalysisPlanLoopCoordinator.java")
                + read(RUNTIME + "OpsAnalysisPlanLifecycle.java")
                + read(RUNTIME + "OpsAnalysisImmediateFollowUpCoordinator.java")
                + read(RUNTIME + "OpsAnalysisReplanTaskRunner.java");

        assertAll(
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Autowired")),
                () -> assertFalse(combined.contains("@Value")),
                () -> assertFalse(combined.contains("JdbcTemplate")),
                () -> assertFalse(combined.contains("ChatClient")));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
