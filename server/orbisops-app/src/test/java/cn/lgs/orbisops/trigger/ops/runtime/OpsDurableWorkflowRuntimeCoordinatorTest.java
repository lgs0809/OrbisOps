package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.workflow.DurableWorkflowRuntimeApplicationService;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunStatus;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsDurableWorkflowRuntimeCoordinatorTest {

    @Test
    void coordinatorMustPersistThroughExistingWorkSessionLeaseAndRecoverLatestCheckpoint() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        AtomicInteger seconds = new AtomicInteger();
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(() ->
                        Instant.parse("2026-08-02T00:00:00Z").plusSeconds(seconds.getAndIncrement()));
        OpsDurableWorkflowRuntimeCoordinator coordinator =
                new OpsDurableWorkflowRuntimeCoordinator(runs, service);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1").sessionId("session-1").projectId("project-1").build();
        BoundWorkflowExecutionPlan plan = plan();

        coordinator.start(request, plan, Map.of("input", "query"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(runs).checkpoint(eq(request), eq("WORKFLOW_RUN_STARTED"), payload.capture());
        when(runs.latestCheckpoint("run-1", "project-1", "WORKFLOW_"))
                .thenReturn(payload.getValue());
        coordinator.cleanup(request);

        assertEquals(DurableWorkflowRunStatus.RUNNING,
                coordinator.recover(request, plan).status());
        verify(runs).checkpoint(eq(request), eq("WORKFLOW_RECOVERED"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void requestIdentityMismatchAndMissingStateMustFailClosed() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        OpsDurableWorkflowRuntimeCoordinator coordinator =
                new OpsDurableWorkflowRuntimeCoordinator(
                        runs, new DurableWorkflowRuntimeApplicationService(Instant::now));
        OpsAgentChatRequest wrongRun = OpsAgentChatRequest.builder()
                .runId("other").sessionId("session-1").projectId("project-1").build();

        assertThrows(IllegalArgumentException.class, () -> coordinator.start(
                wrongRun, plan(), Map.of()));
        assertThrows(IllegalStateException.class, () -> coordinator.beforeNode(
                wrongRun, "start", 1));
    }

    private BoundWorkflowExecutionPlan plan() {
        List<BoundWorkflowNode> nodes = List.of(new BoundWorkflowNode(
                "start", "START", "START", "boundary-node", "config-hash", List.of()));
        List<BoundWorkflowResourceSnapshot> resources = List.of(
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.MEMORY_CONTEXT, "bundle-1", 0,
                        "context-hash", "RUNTIME_CONTEXT_BUNDLE", true, true),
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.RUNTIME_POLICY, "policy-1", 1,
                        "policy-hash", "RUNTIME_CONTEXT_BUNDLE", true, true));
        List<String> stages = List.of("ACCESS_VALIDATION", "BOUND_PLAN_ASSEMBLY");
        BoundWorkflowPlanPolicy policy = new BoundWorkflowPlanPolicy();
        String hash = policy.calculatePlanHash(
                1, 1, "definition-hash", "agent-1", "session-1", "run-1", "project-1",
                "bundle-1", "context-hash", "start", nodes, List.of(), resources, stages);
        return new BoundWorkflowExecutionPlan(
                1, 1, "definition-hash", "agent-1", "session-1", "run-1", "project-1",
                "bundle-1", "context-hash", "start", nodes, List.of(), resources, hash,
                Instant.parse("2026-08-02T00:00:00Z"), stages);
    }
}
