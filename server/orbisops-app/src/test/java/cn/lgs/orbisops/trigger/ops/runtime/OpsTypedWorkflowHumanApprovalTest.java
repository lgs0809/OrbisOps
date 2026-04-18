package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitType;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsTypedWorkflowHumanApprovalTest {

    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Test
    void firstArrivalCreatesDurableWaitAndRaisesNonFailurePendingSignal() {
        Harness harness = harness(false, runningState(DurableWorkflowWaitState.none(), DurableWorkflowNodeStatus.READY));
        WorkflowApprovalRecord record = approval(WorkflowApprovalRecord.Status.WAITING);
        var issued = new WorkflowApprovalApplicationService.IssuedApproval(record, repeat('a'), repeat('b'));
        when(harness.approvals.find("run-1", "approval-node")).thenReturn(Optional.empty());
        when(harness.approvals.issue(any())).thenReturn(issued);
        when(harness.channelBridge.sendIfChannel(any(), any(), eq(issued))).thenReturn(true);

        OpsWorkflowApprovalPendingException pending = assertThrows(
                OpsWorkflowApprovalPendingException.class,
                () -> harness.coordinator.handleHumanApproval(
                        harness.request, harness.node, harness.events, null));

        assertEquals("approval-node", pending.nodeId());
        assertEquals("workflow-approval-1", pending.approvalId());
        verify(harness.durable).beforeNode(harness.request, "approval-node", 3);
        verify(harness.durable).waitFor(
                harness.request, "approval-node", DurableWorkflowWaitType.HUMAN_APPROVAL,
                "wait-hash", record.expiresAt());
        verify(harness.durable, never()).resumeWait(any(), any(), any());
        assertEquals("WORKFLOW_APPROVAL_WAITING", harness.events.get(harness.events.size() - 1).getEventType());
    }

    @Test
    void approvedDecisionResumesDurableWaitAndReturnsStructuredNodeOutput() {
        DurableWorkflowWaitState wait = new DurableWorkflowWaitState(
                DurableWorkflowWaitType.HUMAN_APPROVAL, "approval-node", "wait-hash", NOW,
                NOW.plusSeconds(1800), null, "");
        DurableWorkflowRunState waiting = runningState(wait, DurableWorkflowNodeStatus.RUNNING);
        DurableWorkflowRunState ready = runningState(new DurableWorkflowWaitState(
                DurableWorkflowWaitType.HUMAN_APPROVAL, "approval-node", "wait-hash", NOW,
                NOW.plusSeconds(1800), NOW.plusSeconds(10), "APPROVED"), DurableWorkflowNodeStatus.READY);
        Harness harness = harness(true, waiting);
        WorkflowApprovalRecord approved = approval(WorkflowApprovalRecord.Status.APPROVED);
        when(harness.approvals.find("run-1", "approval-node")).thenReturn(Optional.of(approved));
        when(harness.durable.state(harness.request)).thenReturn(waiting, ready);

        Optional<Map<String, Object>> output = harness.coordinator.handleHumanApproval(
                harness.request, harness.node, harness.events, null);

        assertTrue(output.isPresent());
        assertEquals("APPROVED", output.get().get("decision"));
        assertEquals(true, output.get().get("approved"));
        assertEquals("user-1", output.get().get("decidedBy"));
        verify(harness.durable).resumeWait(harness.request, "wait-hash", "APPROVED");
        verify(harness.durable).beforeNode(harness.request, "approval-node", 3);
        verify(harness.channelBridge, never()).sendIfChannel(any(), any(), any());
        assertEquals("WORKFLOW_APPROVAL_RESUMED", harness.events.get(harness.events.size() - 1).getEventType());
    }

    @Test
    void stillWaitingApprovalNeverFallsThroughToOrdinaryNodeExecution() {
        Harness harness = harness(true, runningState(DurableWorkflowWaitState.none(), DurableWorkflowNodeStatus.RUNNING));
        WorkflowApprovalRecord waiting = approval(WorkflowApprovalRecord.Status.WAITING);
        when(harness.approvals.find("run-1", "approval-node")).thenReturn(Optional.of(waiting));

        assertThrows(OpsWorkflowApprovalPendingException.class,
                () -> harness.coordinator.handleHumanApproval(harness.request, harness.node, harness.events, null));

        verify(harness.durable, never()).beforeNode(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(harness.durable, never()).resumeWait(any(), any(), any());
    }

    private Harness harness(boolean resumed, DurableWorkflowRunState startState) {
        OpsAgentDefinitionValidator validator = mock(OpsAgentDefinitionValidator.class);
        OpsRuntimeWorkflowBindingAdapter binding = mock(OpsRuntimeWorkflowBindingAdapter.class);
        OpsDurableWorkflowRuntimeCoordinator durable = mock(OpsDurableWorkflowRuntimeCoordinator.class);
        OpsRuntimeResourceAssembler resources = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeContextBundleAdapter contextBundles = mock(OpsRuntimeContextBundleAdapter.class);
        WorkflowApprovalApplicationService approvals = mock(WorkflowApprovalApplicationService.class);
        OpsWorkflowApprovalChannelBridge bridge = mock(OpsWorkflowApprovalChannelBridge.class);
        CompiledAgentDefinitionVersion compiled = mock(CompiledAgentDefinitionVersion.class);
        RuntimeContextBundleSnapshot context = mock(RuntimeContextBundleSnapshot.class);
        OpsRuntimeResourceBundle runtime = mock(OpsRuntimeResourceBundle.class);
        BoundWorkflowExecutionPlan plan = mock(BoundWorkflowExecutionPlan.class);
        when(validator.compile(any())).thenReturn(compiled);
        when(compiled.nodes()).thenReturn(List.of());
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
        when(plan.nodes()).thenReturn(List.of());
        when(plan.routes()).thenReturn(List.of());
        if (resumed) when(durable.recover(any(), eq(plan))).thenReturn(startState);
        else when(durable.start(any(), eq(plan), anyMap())).thenReturn(startState);
        OpsRuntimeEventJournal journal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class), mock(GraphEventApplicationService.class), () -> null);
        OpsTypedWorkflowExecutionCoordinator coordinator = new OpsTypedWorkflowExecutionCoordinator(
                validator, binding, durable, resources, contextBundles, approvals, bridge,
                new OpsTypedWorkflowSettings("GUARDED", 3), journal);
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("approval-node")
                .type("HUMAN_APPROVAL")
                .description("Production change approval")
                .config(Map.of("timeoutSeconds", 1800))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .name("Agent")
                .nodes(List.of(node))
                .build();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(Map.of(
                "contextBundleId", "bundle-1",
                "contextBundleHash", "context-hash",
                "durableWorkflow", true));
        if (resumed) metadata.put("resumedFromAttemptId", "attempt-old");
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .userId("user-1")
                .query("approve change")
                .metadata(metadata)
                .build();
        List<OpsRuntimeEvent> events = new ArrayList<>();
        coordinator.begin(definition, request, definition.getNodes(), events, null);
        return new Harness(coordinator, durable, approvals, bridge, request, node, events);
    }

    private DurableWorkflowRunState runningState(DurableWorkflowWaitState wait,
                                                  DurableWorkflowNodeStatus nodeStatus) {
        DurableWorkflowNodeState node = new DurableWorkflowNodeState(
                "approval-node", nodeStatus, nodeStatus == DurableWorkflowNodeStatus.READY ? 0 : 1,
                "", "", "", nodeStatus == DurableWorkflowNodeStatus.READY ? null : NOW, null);
        return new DurableWorkflowRunState(
                "run-1", "project-1", "plan-hash", "definition-hash", "context-hash",
                wait.active() ? DurableWorkflowRunStatus.WAITING_APPROVAL : DurableWorkflowRunStatus.RUNNING,
                "approval-node", Map.of("approval-node", node), List.of(), Map.of(), Map.of(), wait,
                "", "", NOW);
    }

    private WorkflowApprovalRecord approval(WorkflowApprovalRecord.Status status) {
        return new WorkflowApprovalRecord(
                "workflow-approval-1", "run-1", "project-1", "approval-node",
                "wait-hash", "approve-hash", "reject-hash", status,
                "channel-1", "room-1", "Approve production rollout",
                NOW, NOW.plusSeconds(1800),
                status == WorkflowApprovalRecord.Status.WAITING ? "" : "user-1",
                status == WorkflowApprovalRecord.Status.WAITING ? null : NOW.plusSeconds(10));
    }

    private String repeat(char value) {
        return String.valueOf(value).repeat(64);
    }

    private record Harness(OpsTypedWorkflowExecutionCoordinator coordinator,
                           OpsDurableWorkflowRuntimeCoordinator durable,
                           WorkflowApprovalApplicationService approvals,
                           OpsWorkflowApprovalChannelBridge channelBridge,
                           OpsAgentChatRequest request,
                           OpsWorkflowNode node,
                           List<OpsRuntimeEvent> events) {
    }
}
