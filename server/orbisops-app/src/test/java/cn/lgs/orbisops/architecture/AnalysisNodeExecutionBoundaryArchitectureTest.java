package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisNodeExecutionBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void coordinatorMustRemainAProtocolDispatcher() throws IOException {
        String coordinator = read(RUNTIME + "OpsAnalysisNodeExecutionCoordinator.java");

        assertAll(
                () -> assertTrue(coordinator.contains("OpsAnalysisPlanningNodeExecutor")),
                () -> assertTrue(coordinator.contains("OpsAnalysisInvestigationNodeExecutor")),
                () -> assertTrue(coordinator.contains("OpsAnalysisReviewNodeExecutor")),
                () -> assertTrue(coordinator.contains("OpsAnalysisTerminalNodeExecutor")),
                () -> assertTrue(coordinator.contains("OpsAnalysisNodeExecutionContext")),
                () -> assertFalse(coordinator.contains("OpsMainAgentPlanner")),
                () -> assertFalse(coordinator.contains("OpsInvestigationExecutor")),
                () -> assertFalse(coordinator.contains("OpsAnalysisReportComposer")),
                () -> assertFalse(coordinator.contains("OpsChannelNotificationService")),
                () -> assertFalse(coordinator.contains("executeFeedbackReview(")),
                () -> assertFalse(coordinator.contains("triggerImmediateFollowUps(")),
                () -> assertTrue(coordinator.lines().count() < 160));
    }

    @Test
    void nodeProtocolHandlersMustStayBoundedAndFreeOfDynamicInjection() throws IOException {
        String context = read(RUNTIME + "OpsAnalysisNodeExecutionContext.java");
        String lifecycle = read(RUNTIME + "OpsAnalysisNodeLifecycle.java");
        String planning = read(RUNTIME + "OpsAnalysisPlanningNodeExecutor.java");
        String investigation = read(RUNTIME + "OpsAnalysisInvestigationNodeExecutor.java");
        String review = read(RUNTIME + "OpsAnalysisReviewNodeExecutor.java");
        String terminal = read(RUNTIME + "OpsAnalysisTerminalNodeExecutor.java");
        String combined = context + lifecycle + planning + investigation + review + terminal;

        assertAll(
                () -> assertTrue(context.lines().count() < 90),
                () -> assertTrue(lifecycle.lines().count() < 90),
                () -> assertTrue(planning.lines().count() < 170),
                () -> assertTrue(investigation.lines().count() < 110),
                () -> assertTrue(review.lines().count() < 250),
                () -> assertTrue(terminal.lines().count() < 110),
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Value")),
                () -> assertFalse(combined.contains("@Autowired")));
    }

    @Test
    void compositionRootMustOwnTheOnlyDirectCoordinatorConstruction() throws IOException {
        String assembly = read(RUNTIME + "OpsAnalysisNodeExecutionAssembly.java");
        String configuration = read(RUNTIME + "OpsWorkSessionRuntimeConfiguration.java");

        assertAll(
                () -> assertTrue(assembly.contains("new OpsAnalysisNodeExecutionCoordinator(")),
                () -> assertTrue(assembly.contains("new OpsAnalysisPlanningNodeExecutor(")),
                () -> assertTrue(assembly.contains("new OpsAnalysisInvestigationNodeExecutor(")),
                () -> assertTrue(assembly.contains("new OpsAnalysisReviewNodeExecutor(")),
                () -> assertTrue(assembly.contains("new OpsAnalysisTerminalNodeExecutor(")),
                () -> assertTrue(configuration.contains("OpsAnalysisNodeExecutionAssembly.create(")),
                () -> assertFalse(configuration.contains("new OpsAnalysisNodeExecutionCoordinator(")),
                () -> assertTrue(assembly.lines().count() < 90));
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
