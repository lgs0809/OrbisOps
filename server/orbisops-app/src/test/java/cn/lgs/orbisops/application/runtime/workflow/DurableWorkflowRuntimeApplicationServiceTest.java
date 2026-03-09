package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceKind;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowRoute;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitType;
import cn.lgs.orbisops.domain.runtime.workflow.service.BoundWorkflowPlanPolicy;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableWorkflowRuntimeApplicationServiceTest {

    @Test
    void runtimeMustCheckpointNodeRouteLoopWaitAndRecoveryDeterministically() {
        StepClock clock = new StepClock();
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(clock);
        BoundWorkflowExecutionPlan plan = plan("run-1", "definition-hash", "context-hash");

        DurableWorkflowTransition started = service.start(plan, Map.of("input", "query"));
        DurableWorkflowTransition before = service.beforeNode(started.state(), "start", 2);
        assertEquals("run-1:start:1:0",
                service.toolIdempotencyKey(before.state(), "start", 0));
        DurableWorkflowTransition after = service.afterNode(
                before.state(), "start", "output-hash", Map.of("decision", "next"));
        DurableWorkflowTransition routed = service.route(after.state(), plan.routes().get(0));
        DurableWorkflowTransition looped = service.incrementLoop(routed.state(), "review-loop", 2);
        DurableWorkflowTransition nextBefore = service.beforeNode(looped.state(), "next", 2);
        DurableWorkflowTransition waiting = service.waitFor(
                nextBefore.state(), "next", DurableWorkflowWaitType.HUMAN_APPROVAL,
                "approval-token-hash", Instant.parse("2026-08-03T00:00:00Z"));

        assertEquals(DurableWorkflowRunStatus.WAITING_APPROVAL, waiting.state().status());
        assertEquals(DurableWorkflowNodeStatus.WAITING, waiting.state().node("next").status());
        assertThrows(SecurityException.class, () -> service.resumeWait(
                waiting.state(), "wrong-token", "APPROVED"));

        DurableWorkflowTransition resumed = service.resumeWait(
                waiting.state(), "approval-token-hash", "APPROVED");
        Map<String, Object> payload = service.checkpointPayload(resumed.checkpoint());
        DurableWorkflowTransition recovered = service.recover(plan, payload);

        assertEquals(DurableWorkflowRunStatus.RUNNING, recovered.state().status());
        assertEquals(1, recovered.state().routeHistory().size());
        assertEquals(1, recovered.state().loopCounters().get("review-loop"));
        assertEquals("APPROVED", recovered.state().waitState().decision());
    }

    @Test
    void checkpointPayloadMustSurviveFastjsonPersistenceWithoutReferenceAliases() {
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(new StepClock());
        BoundWorkflowExecutionPlan plan = plan("run-json", "definition-hash", "context-hash");
        DurableWorkflowTransition started = service.start(plan, Map.of());
        DurableWorkflowTransition before = service.beforeNode(started.state(), "start", 2);
        Map<String, Object> shared = new LinkedHashMap<>(Map.of("value", "same"));
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("nodeOutput:start", shared);
        updates.put("nodeOutput:start:1", shared);
        DurableWorkflowTransition after = service.afterNode(
                before.state(), "start", "output-hash", updates);

        Map<String, Object> payload = service.checkpointPayload(after.checkpoint());
        String persistedJson = JSON.toJSONString(payload);
        Map<String, Object> restoredPayload = JSON.parseObject(persistedJson, LinkedHashMap.class);
        DurableWorkflowTransition recovered = service.recover(plan, restoredPayload);

        assertFalse(persistedJson.contains("\"$ref\""));
        assertEquals(shared, recovered.state().variables().get("nodeOutput:start"));
        assertEquals(shared, recovered.state().variables().get("nodeOutput:start:1"));
    }

    @Test
    void feedbackLoopMayReenterSucceededNodeAsNextDurableAttempt() {
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(new StepClock());
        BoundWorkflowExecutionPlan plan = plan("run-loop", "definition-hash", "context-hash");
        DurableWorkflowTransition started = service.start(plan, Map.of());
        DurableWorkflowTransition firstBefore = service.beforeNode(started.state(), "start", 3);
        DurableWorkflowTransition firstAfter = service.afterNode(
                firstBefore.state(), "start", "hash-1", Map.of("round", 1));

        DurableWorkflowTransition secondBefore = service.beforeNode(firstAfter.state(), "start", 3);

        assertEquals(DurableWorkflowNodeStatus.RUNNING, secondBefore.state().node("start").status());
        assertEquals(2, secondBefore.state().node("start").attempt());
        assertEquals("run-loop:start:2:0", service.toolIdempotencyKey(secondBefore.state(), "start", 0));
    }

    @Test
    void recoveryMustRetryInFlightNodeWithSameAttemptNumber() {
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(new StepClock());
        BoundWorkflowExecutionPlan plan = plan("run-recover", "definition-hash", "context-hash");
        DurableWorkflowTransition started = service.start(plan, Map.of());
        DurableWorkflowTransition firstBefore = service.beforeNode(started.state(), "start", 3);
        Map<String, Object> payload = service.checkpointPayload(firstBefore.checkpoint());

        DurableWorkflowTransition recovered = service.recover(plan, payload);
        assertEquals(DurableWorkflowNodeStatus.READY, recovered.state().node("start").status());
        assertEquals(0, recovered.state().node("start").attempt());

        DurableWorkflowTransition retried = service.beforeNode(recovered.state(), "start", 3);
        assertEquals(1, retried.state().node("start").attempt());
        assertEquals("run-recover:start:1:0", service.toolIdempotencyKey(retried.state(), "start", 0));
    }

    @Test
    void retryMustBeBoundedAndTerminalRunsMustRejectMutation() {
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(new StepClock());
        BoundWorkflowExecutionPlan plan = plan("run-1", "definition-hash", "context-hash");
        DurableWorkflowTransition start = service.start(plan, Map.of());
        DurableWorkflowTransition before = service.beforeNode(start.state(), "start", 2);
        DurableWorkflowTransition retry = service.failNode(
                before.state(), "start", "TOOL_TIMEOUT", "timeout", true, 2);

        assertEquals(DurableWorkflowNodeStatus.READY, retry.state().node("start").status());
        DurableWorkflowTransition second = service.beforeNode(retry.state(), "start", 2);
        DurableWorkflowTransition failed = service.failNode(
                second.state(), "start", "TOOL_TIMEOUT", "timeout", true, 2);

        assertEquals(DurableWorkflowRunStatus.FAILED, failed.state().status());
        assertThrows(IllegalStateException.class, () -> service.beforeNode(
                failed.state(), "start", 2));
    }

    @Test
    void recoveryMustRejectPlanDriftAndCheckpointTampering() {
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(new StepClock());
        BoundWorkflowExecutionPlan original = plan("run-1", "definition-hash", "context-hash");
        DurableWorkflowTransition started = service.start(original, Map.of());
        Map<String, Object> payload = new LinkedHashMap<>(
                service.checkpointPayload(started.checkpoint()));

        BoundWorkflowExecutionPlan changedDefinition = plan(
                "run-1", "changed-definition", "context-hash");
        assertThrows(IllegalStateException.class, () -> service.recover(
                changedDefinition, payload));

        Map<String, Object> tampered = new LinkedHashMap<>(payload);
        Map<String, Object> state = new LinkedHashMap<>((Map<String, Object>) tampered.get("state"));
        state.put("status", "SUCCEEDED");
        tampered.put("state", state);
        assertThrows(IllegalStateException.class, () -> service.recover(original, tampered));
    }

    @Test
    void recoveryMustRejectMissingOrFutureCheckpointSchemas() {
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(new StepClock());
        BoundWorkflowExecutionPlan plan = plan("run-1", "definition-hash", "context-hash");
        Map<String, Object> payload = new LinkedHashMap<>(service.checkpointPayload(
                service.start(plan, Map.of()).checkpoint()));

        Map<String, Object> missingVersion = new LinkedHashMap<>(payload);
        missingVersion.remove("schemaVersion");
        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                () -> service.recover(plan, missingVersion));
        assertEquals("DURABLE_WORKFLOW_CHECKPOINT_UPCAST_REQUIRED:0", missing.getMessage());

        Map<String, Object> futureVersion = new LinkedHashMap<>(payload);
        futureVersion.put("schemaVersion", 2);
        IllegalStateException future = assertThrows(
                IllegalStateException.class,
                () -> service.recover(plan, futureVersion));
        assertEquals("DURABLE_WORKFLOW_CHECKPOINT_FUTURE_SCHEMA:2", future.getMessage());
    }

    @Test
    void loopAndCancelPoliciesMustFailClosed() {
        DurableWorkflowRuntimeApplicationService service =
                new DurableWorkflowRuntimeApplicationService(new StepClock());
        DurableWorkflowTransition start = service.start(
                plan("run-1", "definition-hash", "context-hash"), Map.of());
        DurableWorkflowTransition once = service.incrementLoop(start.state(), "loop", 1);
        assertThrows(IllegalStateException.class, () -> service.incrementLoop(
                once.state(), "loop", 1));

        DurableWorkflowTransition canceled = service.cancel(once.state(), "operator request");
        assertEquals(DurableWorkflowRunStatus.CANCELED, canceled.state().status());
        assertTrue(canceled.state().nodeStates().values().stream().noneMatch(node ->
                node.status() == DurableWorkflowNodeStatus.RUNNING
                        || node.status() == DurableWorkflowNodeStatus.WAITING
                        || node.status() == DurableWorkflowNodeStatus.READY));
    }

    private BoundWorkflowExecutionPlan plan(
            String runId,
            String definitionHash,
            String contextHash) {
        List<BoundWorkflowNode> nodes = List.of(
                new BoundWorkflowNode("start", "TOOL", "TOOL", "tool-node", "config-a", List.of()),
                new BoundWorkflowNode("next", "HUMAN_APPROVAL", "HUMAN_APPROVAL",
                        "human-approval-node", "config-b", List.of()));
        List<BoundWorkflowRoute> routes = List.of(new BoundWorkflowRoute(
                "edge-1", "start", "next", "FIRST_MATCH", "rule-hash",
                "mapping-hash", 10, false, false));
        List<BoundWorkflowResourceSnapshot> resources = List.of(
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.MEMORY_CONTEXT, "bundle-1", 0,
                        contextHash, "RUNTIME_CONTEXT_BUNDLE", true, true),
                new BoundWorkflowResourceSnapshot(
                        BoundWorkflowResourceKind.RUNTIME_POLICY, "policy-1", 1,
                        "policy-hash", "RUNTIME_CONTEXT_BUNDLE", true, true));
        List<String> stages = List.of("ACCESS_VALIDATION", "BOUND_PLAN_ASSEMBLY");
        BoundWorkflowPlanPolicy policy = new BoundWorkflowPlanPolicy();
        String planHash = policy.calculatePlanHash(
                1, 1, definitionHash, "agent-1", "session-1", runId, "project-1",
                "bundle-1", contextHash, "start", nodes, routes, resources, stages);
        return new BoundWorkflowExecutionPlan(
                1, 1, definitionHash, "agent-1", "session-1", runId, "project-1",
                "bundle-1", contextHash, "start", nodes, routes, resources, planHash,
                Instant.parse("2026-08-02T00:00:00Z"), stages);
    }

    private static final class StepClock implements DurableWorkflowClockPort {
        private final AtomicInteger step = new AtomicInteger();

        @Override
        public Instant now() {
            return Instant.parse("2026-08-02T00:00:00Z").plusSeconds(step.getAndIncrement());
        }
    }
}
