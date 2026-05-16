package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillRuntimeUsageRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsRuntimeSkillLearningCoordinatorTest {

    @Test
    void usefulEvidenceRequiresResultIdAndOutputHash() {
        OpsRuntimeSkillLearningCoordinator coordinator = coordinator(() -> null);
        OpsRuntimeEvent valid = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of("resultId", "result-1", "outputHash", "hash-1"))
                .build();
        OpsRuntimeEvent missingHash = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of("resultId", "result-1"))
                .build();
        OpsRuntimeEvent wrongStatus = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("FAILED")
                .payload(Map.of("resultId", "result-1", "outputHash", "hash-1"))
                .build();

        assertTrue(coordinator.hasUsefulEvidence(List.of(valid)));
        assertFalse(coordinator.hasUsefulEvidence(List.of(missingHash)));
        assertFalse(coordinator.hasUsefulEvidence(List.of(wrongStatus)));
    }

    @Test
    void usefulEvidenceSupportsStructuredJsonOutput() {
        OpsRuntimeSkillLearningCoordinator coordinator = coordinator(() -> null);
        OpsRuntimeEvent valid = OpsRuntimeEvent.builder()
                .eventType("SANDBOX_TEST_FINISHED")
                .status("FOUND")
                .payload(Map.of("output", "{\"resultId\":\"result-2\",\"outputHash\":\"hash-2\"}"))
                .build();
        OpsRuntimeEvent malformed = OpsRuntimeEvent.builder()
                .eventType("SANDBOX_TEST_FINISHED")
                .status("FOUND")
                .payload(Map.of("output", "not-json"))
                .build();

        assertTrue(coordinator.hasUsefulEvidence(List.of(valid)));
        assertFalse(coordinator.hasUsefulEvidence(List.of(malformed)));
    }

    @Test
    void usageOutcomeProjectsToolBlockReplanChangeAndFeedbackFacts() {
        OpsSkillRuntimeUsageRecorder recorder = mock(OpsSkillRuntimeUsageRecorder.class);
        OpsRuntimeSkillLearningCoordinator coordinator = coordinator(() -> recorder);
        OpsAgentChatRequest request = request("run-usage");
        request.getMetadata().put("contextBundleHash", "context-hash");
        request.getMetadata().put("userNegativeFeedback", true);
        request.getMetadata().put("usedSkillVersionRefs", List.of(Map.of(
                "skillId", "skill-1",
                "version", 3,
                "skillHash", "skill-hash",
                "usedAtNode", "node-1")));
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("agent-1").build();
        List<OpsRuntimeEvent> events = new ArrayList<>();
        events.add(evidenceEvent());
        events.add(OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("BLOCKED")
                .summary("策略阻断")
                .build());
        events.add(OpsRuntimeEvent.of("REVIEW_FINISHED", "SUCCEEDED", "NEEDS_REPLAN"));
        events.add(OpsRuntimeEvent.of("CHANGE_PACKAGE_CREATED", "SUCCEEDED", "已创建"));
        ArgumentCaptor<Map<String, Object>> outcomeCaptor = ArgumentCaptor.forClass(Map.class);

        coordinator.recordUsage(request, definition, events, "SUCCEEDED");

        verify(recorder).record(
                eq("project-1"),
                eq("agent-1"),
                eq("run-usage"),
                eq("context-hash"),
                any(),
                outcomeCaptor.capture());
        Map<String, Object> outcome = outcomeCaptor.getValue();
        assertEquals(false, outcome.get("success"));
        assertEquals(true, outcome.get("evidenceSufficient"));
        assertEquals(2L, outcome.get("toolCallCount"));
        assertEquals(1L, outcome.get("blockedToolCallCount"));
        assertEquals(true, outcome.get("needsReplan"));
        assertEquals(true, outcome.get("changePackageCreated"));
        assertEquals(true, outcome.get("userNegativeFeedback"));
    }

    @Test
    void usageRecorderFailureCannotAffectForegroundRuntime() {
        OpsSkillRuntimeUsageRecorder recorder = mock(OpsSkillRuntimeUsageRecorder.class);
        doThrow(new IllegalStateException("usage store unavailable"))
                .when(recorder).record(any(), any(), any(), any(), any(), any());
        OpsRuntimeSkillLearningCoordinator coordinator = coordinator(() -> recorder);
        OpsAgentChatRequest request = request("run-fail-closed");
        request.getMetadata().put("usedSkillVersionRefs", List.of(Map.of(
                "skillId", "skill-1", "version", 1)));

        assertDoesNotThrow(() -> coordinator.recordUsage(
                request,
                OpsAgentDefinition.builder().agentId("agent-1").build(),
                List.of(evidenceEvent()),
                "SUCCEEDED"));
    }

    @Test
    void foregroundRuntimeCannotOwnAutomaticLearningEnqueueMethods() {
        Set<String> declared = Arrays.stream(OpsRuntimeSkillLearningCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertFalse(declared.contains("enqueueEvolution"));
        assertFalse(declared.contains("enqueueEvolutionFromAnalysis"));
        assertFalse(declared.contains("shouldEnqueueEvolution"));

        Set<String> forbiddenLifecycleMethods = Set.of(
                "enqueueSkillEvolution",
                "shouldEnqueueSkillEvolution",
                "enqueueSkillEvolutionFromAnalysis");
        Set<String> lifecycleMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertTrue(forbiddenLifecycleMethods.stream().noneMatch(lifecycleMethods::contains));
    }

    private OpsRuntimeSkillLearningCoordinator coordinator(
            java.util.function.Supplier<OpsSkillRuntimeUsageRecorder> usageSupplier) {
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        return new OpsRuntimeSkillLearningCoordinator(journal, usageSupplier);
    }

    private OpsAgentChatRequest request(String runId) {
        return OpsAgentChatRequest.builder()
                .runId(runId)
                .projectId("project-1")
                .sessionId("session-1")
                .userId("user-1")
                .metadata(new HashMap<>())
                .build();
    }

    private OpsRuntimeEvent evidenceEvent() {
        return OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of("resultId", "result-1", "outputHash", "hash-1"))
                .build();
    }
}
