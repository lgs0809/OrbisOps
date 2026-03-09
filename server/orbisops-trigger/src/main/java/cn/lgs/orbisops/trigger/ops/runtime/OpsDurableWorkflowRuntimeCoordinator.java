package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.workflow.DurableWorkflowRuntimeApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.DurableWorkflowTransition;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowRoute;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Runtime ACL that persists typed durable transitions through the existing Work Session lease. */
@Component
public final class OpsDurableWorkflowRuntimeCoordinator {

    static final String CHECKPOINT_PREFIX = "WORKFLOW_";

    private final OpsWorkSessionRunAdapter workSessionRuns;
    private final DurableWorkflowRuntimeApplicationService runtime;
    private final Map<String, DurableWorkflowRunState> activeStates = new ConcurrentHashMap<>();
    private final ReentrantLock[] runLocks = createRunLocks();

    @Autowired
    public OpsDurableWorkflowRuntimeCoordinator(OpsWorkSessionRunAdapter workSessionRuns) {
        this(workSessionRuns, new DurableWorkflowRuntimeApplicationService(Instant::now));
    }

    OpsDurableWorkflowRuntimeCoordinator(
            OpsWorkSessionRunAdapter workSessionRuns,
            DurableWorkflowRuntimeApplicationService runtime) {
        if (workSessionRuns == null || runtime == null) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_COORDINATOR_DEPENDENCY_REQUIRED");
        }
        this.workSessionRuns = workSessionRuns;
        this.runtime = runtime;
    }

    public DurableWorkflowRunState start(
            OpsAgentChatRequest request,
            BoundWorkflowExecutionPlan plan,
            Map<String, Object> variables) {
        return withRunLock(request, () -> {
            assertRequestPlan(request, plan);
            return persist(request, runtime.start(plan, variables));
        });
    }

    public DurableWorkflowRunState recover(
            OpsAgentChatRequest request,
            BoundWorkflowExecutionPlan plan) {
        return withRunLock(request, () -> {
            assertRequestPlan(request, plan);
            Map<String, Object> payload = workSessionRuns.latestCheckpoint(
                    plan.runId(), plan.projectId(), CHECKPOINT_PREFIX);
            return persist(request, runtime.recover(plan, payload));
        });
    }

    public DurableWorkflowRunState beforeNode(
            OpsAgentChatRequest request,
            String nodeId,
            int maxAttempts) {
        return withRunLock(request, () -> persist(
                request,
                runtime.beforeNode(requireState(request), nodeId, maxAttempts)));
    }

    public DurableWorkflowRunState afterNode(
            OpsAgentChatRequest request,
            String nodeId,
            String outputHash,
            Map<String, Object> variableUpdates) {
        return withRunLock(request, () -> persist(request, runtime.afterNode(
                requireState(request), nodeId, outputHash, variableUpdates)));
    }

    public DurableWorkflowRunState failNode(
            OpsAgentChatRequest request,
            String nodeId,
            String errorCode,
            String errorMessage,
            boolean retryable,
            int maxAttempts) {
        return withRunLock(request, () -> persist(request, runtime.failNode(
                requireState(request), nodeId, errorCode, errorMessage, retryable, maxAttempts)));
    }

    public DurableWorkflowRunState selectRoute(
            OpsAgentChatRequest request,
            BoundWorkflowRoute route) {
        return withRunLock(request, () -> persist(
                request,
                runtime.route(requireState(request), route)));
    }

    public DurableWorkflowRunState incrementLoop(
            OpsAgentChatRequest request,
            String loopId,
            int maxIterations) {
        return withRunLock(request, () -> persist(request, runtime.incrementLoop(
                requireState(request), loopId, maxIterations)));
    }

    public DurableWorkflowRunState waitFor(
            OpsAgentChatRequest request,
            String nodeId,
            DurableWorkflowWaitType type,
            String tokenHash,
            Instant expiresAt) {
        return withRunLock(request, () -> persist(request, runtime.waitFor(
                requireState(request), nodeId, type, tokenHash, expiresAt)));
    }

    public DurableWorkflowRunState resumeWait(
            OpsAgentChatRequest request,
            String tokenHash,
            String decision) {
        return withRunLock(request, () -> persist(request, runtime.resumeWait(
                requireState(request), tokenHash, decision)));
    }

    public DurableWorkflowRunState complete(OpsAgentChatRequest request) {
        return withRunLock(request, () -> persist(
                request,
                runtime.complete(requireState(request))));
    }

    public DurableWorkflowRunState cancel(
            OpsAgentChatRequest request,
            String reason) {
        return withRunLock(request, () -> persist(
                request,
                runtime.cancel(requireState(request), reason)));
    }

    public String toolIdempotencyKey(
            OpsAgentChatRequest request,
            String nodeId,
            int toolCallIndex) {
        return withRunLock(request, () -> runtime.toolIdempotencyKey(
                requireState(request), nodeId, toolCallIndex));
    }

    public DurableWorkflowRunState state(OpsAgentChatRequest request) {
        return withRunLock(request, () -> requireState(request));
    }

    public void cleanup(OpsAgentChatRequest request) {
        if (request == null || request.getRunId() == null) return;
        String runId = request.getRunId().trim();
        if (runId.isBlank()) return;
        ReentrantLock lock = lockFor(runId);
        lock.lock();
        try {
            activeStates.remove(runId);
        } finally {
            lock.unlock();
        }
    }

    private DurableWorkflowRunState persist(
            OpsAgentChatRequest request,
            DurableWorkflowTransition transition) {
        String runId = required(request == null ? null : request.getRunId(),
                "DURABLE_WORKFLOW_REQUEST_RUN_ID_REQUIRED");
        workSessionRuns.checkpoint(
                request,
                CHECKPOINT_PREFIX + transition.checkpoint().type().name(),
                runtime.checkpointPayload(transition.checkpoint()));
        activeStates.put(runId, transition.state());
        return transition.state();
    }

    private DurableWorkflowRunState requireState(OpsAgentChatRequest request) {
        String runId = required(request == null ? null : request.getRunId(),
                "DURABLE_WORKFLOW_REQUEST_RUN_ID_REQUIRED");
        DurableWorkflowRunState state = activeStates.get(runId);
        if (state == null) throw new IllegalStateException("DURABLE_WORKFLOW_STATE_NOT_INITIALIZED:" + runId);
        return state;
    }

    private <T> T withRunLock(OpsAgentChatRequest request, Supplier<T> action) {
        if (action == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_ACTION_REQUIRED");
        String runId = required(request == null ? null : request.getRunId(),
                "DURABLE_WORKFLOW_REQUEST_RUN_ID_REQUIRED");
        ReentrantLock lock = lockFor(runId);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lockFor(String runId) {
        int index = Math.floorMod(runId.hashCode(), runLocks.length);
        return runLocks[index];
    }

    private static ReentrantLock[] createRunLocks() {
        ReentrantLock[] locks = new ReentrantLock[64];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    private void assertRequestPlan(
            OpsAgentChatRequest request,
            BoundWorkflowExecutionPlan plan) {
        if (request == null || plan == null) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_REQUEST_PLAN_REQUIRED");
        }
        if (!required(request.getRunId(), "DURABLE_WORKFLOW_REQUEST_RUN_ID_REQUIRED")
                .equals(plan.runId())) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_REQUEST_RUN_MISMATCH");
        }
        if (request.getSessionId() != null && !request.getSessionId().isBlank()
                && !request.getSessionId().trim().equals(plan.sessionId())) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_REQUEST_SESSION_MISMATCH");
        }
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
