package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;
import cn.lgs.orbisops.domain.runtime.workflow.service.DurableWorkflowRuntimePolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowToolNodeApplicationServiceTest {

    @Test
    void runningNodeMustDeriveAttemptAndIdempotencyKeyFromDurableState() {
        AtomicReference<WorkflowBoundToolInvocation> captured = new AtomicReference<>();
        WorkflowToolNodeApplicationService service = new WorkflowToolNodeApplicationService(
                invocation -> {
                    captured.set(invocation);
                    return new WorkflowBoundToolResult(
                            "result-1", "evidence-1", "output-hash", true,
                            Map.of("status", "ok"));
                });
        DurableWorkflowRunState running = runningState();
        BoundWorkflowResourceSnapshot resource = toolResource();

        WorkflowBoundToolResult result = service.execute(
                running, "actor-1", "session-1", "tool-node", 2,
                "toolset-1", "inspect", resource,
                Map.of("target", "mysql"), true, false);

        assertEquals("result-1", result.resultId());
        assertEquals("run-1:tool-node:1:2", captured.get().idempotencyKey());
        assertEquals(1, captured.get().attempt());
        assertEquals(resource, captured.get().boundResource());
    }

    @Test
    void nonRunningNodeAndDirectWriteMustFailClosed() {
        WorkflowToolNodeApplicationService service = new WorkflowToolNodeApplicationService(
                invocation -> new WorkflowBoundToolResult(
                        "result-1", "evidence-1", "output-hash", true, Map.of()));
        BoundWorkflowExecutionPlan plan = plan();
        DurableWorkflowRunState ready = new DurableWorkflowRuntimePolicy().start(
                plan, Map.of(), Instant.parse("2026-08-02T00:00:00Z"));

        assertThrows(IllegalStateException.class, () -> service.execute(
                ready, "actor-1", "session-1", "tool-node", 0,
                "toolset-1", "inspect", toolResource(), Map.of(), true, false));

        DurableWorkflowRunState running = new DurableWorkflowRuntimePolicy().beforeNode(
                ready, "tool-node", 1, Instant.parse("2026-08-02T00:00:01Z"));
        assertThrows(SecurityException.class, () -> service.execute(
                running, "actor-1", "session-1", "tool-node", 0,
                "toolset-1", "write", toolResource(), Map.of(), false, false));
    }

    private DurableWorkflowRunState runningState() {
        DurableWorkflowRuntimePolicy policy = new DurableWorkflowRuntimePolicy();
        DurableWorkflowRunState ready = policy.start(
                plan(), Map.of(), Instant.parse("2026-08-02T00:00:00Z"));
        return policy.beforeNode(
                ready, "tool-node", 2, Instant.parse("2026-08-02T00:00:01Z"));
    }

    private BoundWorkflowExecutionPlan plan() {
        BoundWorkflowResourceSnapshot toolResource = toolResource();
        List<BoundWorkflowNode> nodes = List.of(new BoundWorkflowNode(
                "tool-node", "TOOL", "TOOL", "tool-node", "config-hash",
                List.of(toolResource)));
        List<BoundWorkflowResourceSnapshot> resources = List.of(
                toolResource,
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
                "bundle-1", "context-hash", "tool-node", nodes, List.of(), resources, stages);
        return new BoundWorkflowExecutionPlan(
                1, 1, "definition-hash", "agent-1", "session-1", "run-1", "project-1",
                "bundle-1", "context-hash", "tool-node", nodes, List.of(), resources,
                hash, Instant.parse("2026-08-02T00:00:00Z"), stages);
    }

    private BoundWorkflowResourceSnapshot toolResource() {
        return new BoundWorkflowResourceSnapshot(
                BoundWorkflowResourceKind.TOOL, "tool-resource", 7,
                "tool-definition-hash", "TOOLSET_SNAPSHOT", true, true);
    }
}
