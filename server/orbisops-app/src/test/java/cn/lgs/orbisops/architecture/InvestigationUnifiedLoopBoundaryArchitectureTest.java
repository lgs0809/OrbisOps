package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationUnifiedLoopBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void executorIsEntryAdapterAndBothModesShareOneBudgetedSessionLoop() throws IOException {
        String executor = read(OPS + "OpsInvestigationExecutor.java");
        String loop = read(OPS + "OpsInvestigationLoopService.java");
        String session = read(OPS + "OpsInvestigationLoopSession.java");
        String routing = read(OPS + "OpsInvestigationObservationRoutingService.java");
        String resolver = read(OPS + "OpsInvestigationTaskResolver.java");
        String notes = read(OPS + "OpsInvestigationLoopNotes.java");

        assertAll(
                () -> assertTrue(executor.contains("OpsInvestigationLoopService loopService")),
                () -> assertTrue(executor.contains("loopService.executeInitial(")),
                () -> assertTrue(executor.contains("loopService.executeFollowUps(")),
                () -> assertFalse(executor.contains("while (")),
                () -> assertFalse(executor.contains("OpsInvestigationLoopSession")),
                () -> assertFalse(executor.contains("ReflectionOutcome")),
                () -> assertTrue(executor.lines().count() <= 230),
                () -> assertTrue(loop.contains("Outcome executeInitial(")),
                () -> assertTrue(loop.contains("Outcome executeFollowUps(")),
                () -> assertTrue(loop.contains("OpsInvestigationLoopSession session = start(input)")),
                () -> assertTrue(loop.contains("inspectAvailableResults(")),
                () -> assertTrue(loop.contains("drainQueue(")),
                () -> assertTrue(loop.contains("private record Input(")),
                () -> assertTrue(loop.contains("private enum Mode")),
                () -> assertEquals(1, occurrences(loop, "while (!session.queue.isEmpty()")),
                () -> assertFalse(loop.contains("private static final class Session")),
                () -> assertFalse(loop.contains("ReflectionOutcome")),
                () -> assertFalse(loop.contains("reflectionService.reflect(")),
                () -> assertFalse(loop.contains("followUpService.decide(")),
                () -> assertTrue(loop.lines().count() <= 350),
                () -> assertFalse(loop.contains("@Service")),
                () -> assertFalse(loop.contains("@Value")),
                () -> assertTrue(session.contains("final List<OpsAnalysisResponseDTO.InvestigationResultDTO> results")),
                () -> assertTrue(session.contains("final Set<String> executedSources")),
                () -> assertTrue(session.contains("final Deque<OpsAnalysisResponseDTO.InvestigationTaskDTO> queue")),
                () -> assertTrue(session.contains("int adjustments")),
                () -> assertTrue(session.contains("int guard")),
                () -> assertFalse(session.contains("@Service")),
                () -> assertTrue(routing.contains("reflectionService.reflect(")),
                () -> assertTrue(routing.contains("followUpService.decide(")),
                () -> assertTrue(routing.lines().count() <= 350),
                () -> assertFalse(routing.contains("@Service")),
                () -> assertTrue(resolver.contains("plan.getTasks()")),
                () -> assertTrue(resolver.contains("plan.getConditionalTasks()")),
                () -> assertTrue(resolver.contains("main reflection")),
                () -> assertFalse(resolver.contains("@Service")),
                () -> assertTrue(loop.contains("OpsInvestigationLoopNotes notes")),
                () -> assertTrue(loop.contains("notes.initial(")),
                () -> assertTrue(loop.contains("notes.executionLimit(")),
                () -> assertFalse(loop.contains("初始选择数据源：")),
                () -> assertTrue(notes.contains("初始选择数据源：")),
                () -> assertTrue(notes.contains("follow-up 调整查询返回")),
                () -> assertFalse(notes.contains("@Service")));
    }

    private int occurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
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
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
