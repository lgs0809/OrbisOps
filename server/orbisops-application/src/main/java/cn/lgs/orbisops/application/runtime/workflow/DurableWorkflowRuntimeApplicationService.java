package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowRoute;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowCheckpoint;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowCheckpointType;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitType;
import cn.lgs.orbisops.domain.runtime.workflow.service.DurableWorkflowRuntimePolicy;

import java.time.Instant;
import java.util.Map;

public final class DurableWorkflowRuntimeApplicationService {

    private final DurableWorkflowRuntimePolicy policy;
    private final DurableWorkflowCheckpointCodec codec;
    private final DurableWorkflowClockPort clock;

    public DurableWorkflowRuntimeApplicationService(DurableWorkflowClockPort clock) {
        this(new DurableWorkflowRuntimePolicy(), new DurableWorkflowCheckpointCodec(), clock);
    }

    DurableWorkflowRuntimeApplicationService(
            DurableWorkflowRuntimePolicy policy,
            DurableWorkflowCheckpointCodec codec,
            DurableWorkflowClockPort clock) {
        if (policy == null || codec == null || clock == null) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_RUNTIME_DEPENDENCY_REQUIRED");
        }
        this.policy = policy;
        this.codec = codec;
        this.clock = clock;
    }

    public DurableWorkflowTransition start(
            BoundWorkflowExecutionPlan plan,
            Map<String, Object> variables) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.RUN_STARTED,
                policy.start(plan, variables, now), now);
    }

    public DurableWorkflowTransition beforeNode(
            DurableWorkflowRunState state,
            String nodeId,
            int maxAttempts) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.NODE_BEFORE,
                policy.beforeNode(state, nodeId, maxAttempts, now), now);
    }

    public DurableWorkflowTransition afterNode(
            DurableWorkflowRunState state,
            String nodeId,
            String outputHash,
            Map<String, Object> variableUpdates) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.NODE_AFTER,
                policy.afterNode(state, nodeId, outputHash, variableUpdates, now), now);
    }

    public DurableWorkflowTransition failNode(
            DurableWorkflowRunState state,
            String nodeId,
            String errorCode,
            String errorMessage,
            boolean retryable,
            int maxAttempts) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.NODE_FAILED,
                policy.failNode(state, nodeId, errorCode, errorMessage,
                        retryable, maxAttempts, now), now);
    }

    public DurableWorkflowTransition route(
            DurableWorkflowRunState state,
            BoundWorkflowRoute route) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.ROUTE_SELECTED,
                policy.selectRoute(state, route, now), now);
    }

    public DurableWorkflowTransition incrementLoop(
            DurableWorkflowRunState state,
            String loopId,
            int maxIterations) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.LOOP_INCREMENTED,
                policy.incrementLoop(state, loopId, maxIterations, now), now);
    }

    public DurableWorkflowTransition waitFor(
            DurableWorkflowRunState state,
            String nodeId,
            DurableWorkflowWaitType waitType,
            String tokenHash,
            Instant expiresAt) {
        Instant now = clock.now();
        DurableWorkflowCheckpointType type = waitType == DurableWorkflowWaitType.HUMAN_APPROVAL
                ? DurableWorkflowCheckpointType.APPROVAL_WAITING
                : DurableWorkflowCheckpointType.WAITING;
        return transition(type,
                policy.waitFor(state, nodeId, waitType, tokenHash, expiresAt, now), now);
    }

    public DurableWorkflowTransition resumeWait(
            DurableWorkflowRunState state,
            String tokenHash,
            String decision) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.APPROVAL_RESUMED,
                policy.resumeWait(state, tokenHash, decision, now), now);
    }

    public DurableWorkflowTransition complete(DurableWorkflowRunState state) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.RUN_SUCCEEDED,
                policy.complete(state, now), now);
    }

    public DurableWorkflowTransition cancel(
            DurableWorkflowRunState state,
            String reason) {
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.RUN_CANCELED,
                policy.cancel(state, reason, now), now);
    }

    public DurableWorkflowTransition recover(
            BoundWorkflowExecutionPlan plan,
            Map<String, Object> checkpointPayload) {
        DurableWorkflowCheckpoint checkpoint = codec.decode(checkpointPayload);
        Instant now = clock.now();
        return transition(DurableWorkflowCheckpointType.RECOVERED,
                policy.recover(plan, checkpoint.state(), now), now);
    }

    public String toolIdempotencyKey(
            DurableWorkflowRunState state,
            String nodeId,
            int toolCallIndex) {
        return policy.toolIdempotencyKey(state, nodeId, toolCallIndex);
    }

    public Map<String, Object> checkpointPayload(DurableWorkflowCheckpoint checkpoint) {
        return codec.encode(checkpoint);
    }

    private DurableWorkflowTransition transition(
            DurableWorkflowCheckpointType type,
            DurableWorkflowRunState state,
            Instant now) {
        return new DurableWorkflowTransition(state, codec.checkpoint(type, state, now));
    }
}
