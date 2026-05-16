package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitState;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsTypedWorkflowRecoveryTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void resumedRunMustRecoverPinnedPlanAndReplayCompletedNodeOutput(int completedAttempt) {
        OpsAgentDefinitionValidator validator = mock(OpsAgentDefinitionValidator.class);
        OpsRuntimeWorkflowBindingAdapter binding = mock(OpsRuntimeWorkflowBindingAdapter.class);
        OpsDurableWorkflowRuntimeCoordinator durable = mock(OpsDurableWorkflowRuntimeCoordinator.class);
        OpsRuntimeResourceAssembler resources = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeContextBundleAdapter contextBundles = mock(OpsRuntimeContextBundleAdapter.class);
        CompiledAgentDefinitionVersion compiled = mock(CompiledAgentDefinitionVersion.class);
        RuntimeContextBundleSnapshot context = mock(RuntimeContextBundleSnapshot.class);
        OpsRuntimeResourceBundle runtime = mock(OpsRuntimeResourceBundle.class);
        BoundWorkflowExecutionPlan plan = mock(BoundWorkflowExecutionPlan.class);
        BoundWorkflowNode boundNode = mock(BoundWorkflowNode.class);
        Map<String, Object> cachedOutput = Map.of("output", "cached answer");
        String outputHash = CanonicalObjectHasher.sha256(cachedOutput);
        DurableWorkflowNodeState nodeState = new DurableWorkflowNodeState(
                "node-1",
                DurableWorkflowNodeStatus.SUCCEEDED,
                completedAttempt,
                outputHash,
                "",
                "",
                Instant.parse("2026-08-02T08:00:00Z"),
                Instant.parse("2026-08-02T08:00:01Z"));
        DurableWorkflowRunState recovered = new DurableWorkflowRunState(
                "run-1",
                "project-1",
                "plan-hash",
                "definition-hash",
                "context-hash",
                DurableWorkflowRunStatus.RUNNING,
                "node-1",
                Map.of("node-1", nodeState),
                List.of(),
                Map.of(),
                completedAttempt == 1 ? Map.of("nodeOutput:node-1", cachedOutput)
                        : Map.of("nodeOutput:node-1:" + completedAttempt, cachedOutput,
                        "nodeOutputHash:node-1:" + completedAttempt, outputHash),
                DurableWorkflowWaitState.none(),
                "",
                "",
                Instant.parse("2026-08-02T08:00:02Z"));

        when(validator.compile(any())).thenReturn(compiled);
        when(contextBundles.requireSnapshot("bundle-1", "context-hash")).thenReturn(context);
        when(resources.assembleAgent(any(), any(), any(), any())).thenReturn(runtime);
        when(binding.bind(any(), any(), anyMap(), any(), any(), any(), any())).thenReturn(plan);
        when(plan.planHash()).thenReturn("plan-hash");
        when(plan.runId()).thenReturn("run-1");
        when(plan.projectId()).thenReturn("project-1");
        when(plan.sessionId()).thenReturn("session-1");
        when(plan.agentId()).thenReturn("agent-1");
        when(plan.contextBundleId()).thenReturn("bundle-1");
        when(plan.definitionVersion()).thenReturn(1);
        when(compiled.agentId()).thenReturn("agent-1");
        when(compiled.definitionVersion()).thenReturn(1);
        when(compiled.definitionHash()).thenReturn("definition-hash");
        when(plan.definitionHash()).thenReturn("definition-hash");
        when(plan.contextBundleHash()).thenReturn("context-hash");
        when(plan.nodes()).thenReturn(List.of(boundNode));
        when(plan.routes()).thenReturn(List.of());
        when(durable.recover(any(), any())).thenReturn(recovered);
        when(durable.state(any())).thenReturn(recovered);

        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        OpsTypedWorkflowExecutionCoordinator coordinator =
                new OpsTypedWorkflowExecutionCoordinator(
                        validator,
                        binding,
                        durable,
                        resources,
                        contextBundles,
                        mock(cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService.class),
                        mock(OpsWorkflowApprovalChannelBridge.class),
                        new OpsTypedWorkflowSettings("GUARDED", 3),
                        journal);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .name("Agent")
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("node-1")
                        .type("CHAT")
                        .build()))
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .query("hello")
                .metadata(new java.util.LinkedHashMap<>(Map.of(
                        "contextBundleId", "bundle-1",
                        "contextBundleHash", "context-hash",
                        "resumedFromAttemptId", "attempt-1")))
                .build();
        List<OpsRuntimeEvent> events = new ArrayList<>();

        coordinator.begin(definition, request, definition.getNodes(), events, null);
        Optional<Map<String, Object>> replayed = coordinator.replayCompletedNode(
                request, definition.getNodes().get(0), events, null);

        verify(durable).recover(request, plan);
        verify(durable, never()).start(any(), any(), anyMap());
        verify(durable, never()).beforeNode(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        assertTrue(replayed.isPresent());
        assertEquals(cachedOutput, replayed.get());
        assertEquals("TYPED_WORKFLOW_NODE_REPLAYED",
                events.get(events.size() - 1).getEventType());
        assertEquals(completedAttempt, events.get(events.size() - 1).getPayload().get("attempt"));
        assertTrue(coordinator.replayCompletedNode(request, definition.getNodes().get(0), events, null).isEmpty());
    }
}
