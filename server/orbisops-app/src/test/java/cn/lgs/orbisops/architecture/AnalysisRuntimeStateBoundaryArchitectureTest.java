package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisRuntimeStateBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void managerMustOwnOnlyCanonicalRegistryAndBoundaryDelegation() throws IOException {
        String manager = read(RUNTIME + "OpsAnalysisRuntimeStateManager.java");

        assertAll(
                () -> assertTrue(manager.contains(
                        "private final OpsAnalysisRuntimeStateFactory stateFactory;")),
                () -> assertTrue(manager.contains(
                        "private final OpsAnalysisRuntimeEventRecorder eventRecorder;")),
                () -> assertTrue(manager.contains(
                        "private final OpsAnalysisResponseNotes responseNotes;")),
                () -> assertTrue(manager.contains("ConcurrentHashMap")),
                () -> assertTrue(manager.contains("states.computeIfAbsent")),
                () -> assertTrue(manager.contains("stateFactory.create(")),
                () -> assertTrue(manager.contains("eventRecorder.publishRunStarted(")),
                () -> assertTrue(manager.contains("eventRecorder.recordStep(")),
                () -> assertFalse(manager.contains("ObjectProvider")),
                () -> assertFalse(manager.contains("OpsSkillToolProvider")),
                () -> assertFalse(manager.contains("GraphEventApplicationService")),
                () -> assertFalse(manager.contains("OpsRunCancellationRegistry")),
                () -> assertFalse(manager.contains("JSON.toJSONString")),
                () -> assertFalse(manager.contains("DateTimeFormatter")),
                () -> assertFalse(manager.contains("listSkillSummaries")),
                () -> assertEquals(1, occurrences(
                        manager, "OpsAnalysisRuntimeStateManager(")),
                () -> assertTrue(manager.lines().count() < 190));
    }

    @Test
    void stateFactoryMustOwnSnapshotQuestionContextAndMutableAggregateCreation() throws IOException {
        String factory = read(RUNTIME + "OpsAnalysisRuntimeStateFactory.java");

        assertAll(
                () -> assertTrue(factory.contains("JSON.toJSONString(definition)")),
                () -> assertTrue(factory.contains("OpsQuestionContext.from(request.getQuery())")),
                () -> assertTrue(factory.contains("skillReadinessInspector.inspect(definition)")),
                () -> assertTrue(factory.contains("Collections.synchronizedList")),
                () -> assertTrue(factory.contains("Collections.synchronizedSet")),
                () -> assertTrue(factory.contains("new AtomicReference")),
                () -> assertFalse(factory.contains("ConcurrentHashMap")),
                () -> assertFalse(factory.contains("GraphEventApplicationService")),
                () -> assertFalse(factory.contains("ObjectProvider")),
                () -> assertTrue(factory.lines().count() < 90));
    }

    @Test
    void eventRecorderMustOwnRunNodeBudgetFollowUpAndCancellationEvents() throws IOException {
        String recorder = read(RUNTIME + "OpsAnalysisRuntimeEventRecorder.java");

        assertAll(
                () -> assertTrue(recorder.contains("GraphEventApplicationService")),
                () -> assertTrue(recorder.contains("OpsRunCancellationRegistry")),
                () -> assertTrue(recorder.contains("RUN_STARTED")),
                () -> assertTrue(recorder.contains("RUN_FINISHED")),
                () -> assertTrue(recorder.contains("NODE_FINISHED")),
                () -> assertTrue(recorder.contains("NODE_BUDGET_EXCEEDED")),
                () -> assertTrue(recorder.contains("FOLLOW_UP_SUB_AGENT")),
                () -> assertTrue(recorder.contains("cancellationRegistry.assertNotCanceled")),
                () -> assertTrue(recorder.contains("DateTimeFormatter")),
                () -> assertFalse(recorder.contains("ObjectProvider")),
                () -> assertFalse(recorder.contains("OpsSkillToolProvider")),
                () -> assertFalse(recorder.contains("ConcurrentHashMap")),
                () -> assertTrue(recorder.lines().count() < 180));
    }

    @Test
    void skillInspectorAndNotesMustRemainFocusedPolicies() throws IOException {
        String skills = read(RUNTIME + "OpsAnalysisSkillReadinessInspector.java");
        String notes = read(RUNTIME + "OpsAnalysisResponseNotes.java");

        assertAll(
                () -> assertTrue(skills.contains("Supplier<SkillRuntimeToolProvider>")),
                () -> assertTrue(skills.contains("listSkillSummaries()")),
                () -> assertTrue(skills.contains("Agent Skill 缺失")),
                () -> assertFalse(skills.contains("ObjectProvider")),
                () -> assertFalse(skills.contains("GraphEventApplicationService")),
                () -> assertTrue(skills.lines().count() < 70),
                () -> assertTrue(notes.contains("synchronized (response)")),
                () -> assertTrue(notes.contains("mergeRuntimeNotes(")),
                () -> assertFalse(notes.contains("OpsRuntimeEvent")),
                () -> assertFalse(notes.contains("ObjectProvider")),
                () -> assertTrue(notes.lines().count() < 60));
    }

    @Test
    void compositionRootMustCreateTheSingleTypedStateManagerGraph() throws IOException {
        String configuration = read(RUNTIME + "OpsWorkSessionRuntimeConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains(
                        "new OpsAnalysisRuntimeEventRecorder(")),
                () -> assertTrue(configuration.contains(
                        "new OpsAnalysisRuntimeStateFactory(")),
                () -> assertTrue(configuration.contains(
                        "new OpsAnalysisSkillReadinessInspector(")),
                () -> assertTrue(configuration.contains(
                        "skillToolProvider::getIfAvailable")),
                () -> assertTrue(configuration.contains(
                        "new OpsAnalysisResponseNotes()")),
                () -> assertEquals(1, occurrences(
                        configuration, "new OpsAnalysisRuntimeStateManager(")));
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
