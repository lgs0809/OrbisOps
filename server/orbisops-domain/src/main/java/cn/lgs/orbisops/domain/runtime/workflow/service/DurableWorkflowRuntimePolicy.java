package cn.lgs.orbisops.domain.runtime.workflow.service;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowRoute;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRouteDecision;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic workflow state transitions. Persistence and execution stay outside the domain. */
public final class DurableWorkflowRuntimePolicy {

    public DurableWorkflowRunState start(
            BoundWorkflowExecutionPlan plan,
            Map<String, Object> variables,
            Instant now) {
        if (plan == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_PLAN_REQUIRED");
        requiredTime(now);
        new BoundWorkflowPlanPolicy().assertPlanHash(plan);
        LinkedHashMap<String, DurableWorkflowNodeState> states = new LinkedHashMap<>();
        plan.nodes().forEach(node -> states.put(node.nodeId(), new DurableWorkflowNodeState(
                node.nodeId(), node.nodeId().equals(plan.startNodeId())
                ? DurableWorkflowNodeStatus.READY : DurableWorkflowNodeStatus.PENDING,
                0, "", "", "", null, null)));
        return new DurableWorkflowRunState(
                plan.runId(), plan.projectId(), plan.planHash(), plan.definitionHash(),
                plan.contextBundleHash(), DurableWorkflowRunStatus.READY, "",
                states, List.of(), Map.of(), variables, DurableWorkflowWaitState.none(),
                "", "", now);
    }

    public DurableWorkflowRunState beforeNode(
            DurableWorkflowRunState state,
            String nodeId,
            int maxAttempts,
            Instant now) {
        assertMutable(state);
        if (state.waitState().active()) throw new IllegalStateException("DURABLE_WORKFLOW_WAIT_ACTIVE");
        DurableWorkflowNodeState current = state.node(nodeId);
        if (current.status() == DurableWorkflowNodeStatus.SKIPPED
                || current.status() == DurableWorkflowNodeStatus.CANCELED) {
            throw new IllegalStateException("DURABLE_WORKFLOW_NODE_ALREADY_TERMINAL:" + nodeId);
        }
        // A SUCCEEDED node may be entered again through a bounded feedback loop. Each
        // traversal becomes the next durable attempt; recovery replays completed attempts
        // from attempt-scoped output snapshots before a later attempt is executed.
        int attempt = current.attempt() + 1;
        if (attempt > Math.max(1, maxAttempts)) {
            throw new IllegalStateException("DURABLE_WORKFLOW_NODE_ATTEMPTS_EXHAUSTED:" + nodeId);
        }
        DurableWorkflowNodeState nextNode = current.withStatus(
                DurableWorkflowNodeStatus.RUNNING, attempt, "", "", "", now, null);
        return copy(state, DurableWorkflowRunStatus.RUNNING, nodeId,
                replace(state.nodeStates(), nodeId, nextNode), state.routeHistory(),
                state.loopCounters(), state.variables(), DurableWorkflowWaitState.none(),
                "", "", now);
    }

    public DurableWorkflowRunState afterNode(
            DurableWorkflowRunState state,
            String nodeId,
            String outputHash,
            Map<String, Object> variableUpdates,
            Instant now) {
        assertMutable(state);
        DurableWorkflowNodeState current = state.node(nodeId);
        if (current.status() != DurableWorkflowNodeStatus.RUNNING) {
            throw new IllegalStateException("DURABLE_WORKFLOW_NODE_NOT_RUNNING:" + nodeId);
        }
        DurableWorkflowNodeState nextNode = current.withStatus(
                DurableWorkflowNodeStatus.SUCCEEDED, current.attempt(), text(outputHash),
                "", "", current.startedAt(), now);
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>(state.variables());
        if (variableUpdates != null) variables.putAll(variableUpdates);
        return copy(state, DurableWorkflowRunStatus.RUNNING, "",
                replace(state.nodeStates(), nodeId, nextNode), state.routeHistory(),
                state.loopCounters(), variables, DurableWorkflowWaitState.none(),
                "", "", now);
    }

    public DurableWorkflowRunState failNode(
            DurableWorkflowRunState state,
            String nodeId,
            String errorCode,
            String errorMessage,
            boolean retryable,
            int maxAttempts,
            Instant now) {
        assertMutable(state);
        DurableWorkflowNodeState current = state.node(nodeId);
        if (current.status() != DurableWorkflowNodeStatus.RUNNING) {
            throw new IllegalStateException("DURABLE_WORKFLOW_NODE_NOT_RUNNING:" + nodeId);
        }
        boolean retry = retryable && current.attempt() < Math.max(1, maxAttempts);
        DurableWorkflowNodeState nextNode = current.withStatus(
                retry ? DurableWorkflowNodeStatus.READY : DurableWorkflowNodeStatus.FAILED,
                current.attempt(), "", required(errorCode, "DURABLE_WORKFLOW_ERROR_CODE_REQUIRED"),
                text(errorMessage), current.startedAt(), now);
        return copy(state,
                retry ? DurableWorkflowRunStatus.RUNNING : DurableWorkflowRunStatus.FAILED,
                "", replace(state.nodeStates(), nodeId, nextNode), state.routeHistory(),
                state.loopCounters(), state.variables(), DurableWorkflowWaitState.none(),
                retry ? "" : errorCode, retry ? "" : errorMessage, now);
    }

    public DurableWorkflowRunState selectRoute(
            DurableWorkflowRunState state,
            BoundWorkflowRoute route,
            Instant now) {
        assertMutable(state);
        if (route == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_ROUTE_REQUIRED");
        DurableWorkflowNodeState from = state.node(route.fromNodeId());
        DurableWorkflowNodeState to = state.node(route.toNodeId());
        if (from.status() != DurableWorkflowNodeStatus.SUCCEEDED) {
            throw new IllegalStateException("DURABLE_WORKFLOW_ROUTE_SOURCE_NOT_SUCCEEDED:" + route.fromNodeId());
        }
        List<DurableWorkflowRouteDecision> routes = new ArrayList<>(state.routeHistory());
        routes.add(new DurableWorkflowRouteDecision(
                route.edgeId(), route.fromNodeId(), route.toNodeId(), from.attempt(),
                route.ruleHash(), now));
        Map<String, DurableWorkflowNodeState> nodes = state.nodeStates();
        if (to.status() == DurableWorkflowNodeStatus.PENDING) {
            nodes = replace(nodes, to.nodeId(), to.withStatus(
                    DurableWorkflowNodeStatus.READY, to.attempt(), to.outputHash(),
                    to.errorCode(), to.errorMessage(), to.startedAt(), to.finishedAt()));
        }
        return copy(state, DurableWorkflowRunStatus.RUNNING, "", nodes, routes,
                state.loopCounters(), state.variables(), state.waitState(), "", "", now);
    }

    public DurableWorkflowRunState incrementLoop(
            DurableWorkflowRunState state,
            String loopId,
            int maxIterations,
            Instant now) {
        assertMutable(state);
        String id = required(loopId, "DURABLE_WORKFLOW_LOOP_ID_REQUIRED");
        int next = state.loopCounters().getOrDefault(id, 0) + 1;
        if (next > Math.max(1, maxIterations)) {
            throw new IllegalStateException("DURABLE_WORKFLOW_LOOP_LIMIT_EXCEEDED:" + id);
        }
        LinkedHashMap<String, Integer> counters = new LinkedHashMap<>(state.loopCounters());
        counters.put(id, next);
        return copy(state, state.status(), state.currentNodeId(), state.nodeStates(),
                state.routeHistory(), counters, state.variables(), state.waitState(),
                state.errorCode(), state.errorMessage(), now);
    }

    public DurableWorkflowRunState waitFor(
            DurableWorkflowRunState state,
            String nodeId,
            DurableWorkflowWaitType waitType,
            String tokenHash,
            Instant expiresAt,
            Instant now) {
        assertMutable(state);
        if (waitType == null || waitType == DurableWorkflowWaitType.NONE) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_WAIT_TYPE_REQUIRED");
        }
        DurableWorkflowNodeState current = state.node(nodeId);
        if (current.status() != DurableWorkflowNodeStatus.RUNNING) {
            throw new IllegalStateException("DURABLE_WORKFLOW_NODE_NOT_RUNNING:" + nodeId);
        }
        DurableWorkflowNodeState waiting = current.withStatus(
                DurableWorkflowNodeStatus.WAITING, current.attempt(), current.outputHash(),
                "", "", current.startedAt(), null);
        DurableWorkflowWaitState wait = new DurableWorkflowWaitState(
                waitType, nodeId, required(tokenHash, "DURABLE_WORKFLOW_WAIT_TOKEN_REQUIRED"),
                now, expiresAt, null, "");
        DurableWorkflowRunStatus status = waitType == DurableWorkflowWaitType.HUMAN_APPROVAL
                ? DurableWorkflowRunStatus.WAITING_APPROVAL : DurableWorkflowRunStatus.WAITING;
        return copy(state, status, nodeId, replace(state.nodeStates(), nodeId, waiting),
                state.routeHistory(), state.loopCounters(), state.variables(), wait,
                "", "", now);
    }

    public DurableWorkflowRunState resumeWait(
            DurableWorkflowRunState state,
            String tokenHash,
            String decision,
            Instant now) {
        if (!state.waitState().active()) throw new IllegalStateException("DURABLE_WORKFLOW_WAIT_NOT_ACTIVE");
        if (!state.waitState().tokenHash().equals(required(tokenHash, "DURABLE_WORKFLOW_WAIT_TOKEN_REQUIRED"))) {
            throw new SecurityException("DURABLE_WORKFLOW_WAIT_TOKEN_MISMATCH");
        }
        String nodeId = state.waitState().nodeId();
        DurableWorkflowNodeState current = state.node(nodeId);
        DurableWorkflowNodeState ready = current.withStatus(
                DurableWorkflowNodeStatus.READY, current.attempt(), current.outputHash(),
                "", "", current.startedAt(), null);
        DurableWorkflowWaitState resumed = new DurableWorkflowWaitState(
                state.waitState().type(), nodeId, state.waitState().tokenHash(),
                state.waitState().requestedAt(), state.waitState().expiresAt(), now, decision);
        return copy(state, DurableWorkflowRunStatus.RUNNING, "",
                replace(state.nodeStates(), nodeId, ready), state.routeHistory(),
                state.loopCounters(), state.variables(), resumed, "", "", now);
    }

    public DurableWorkflowRunState complete(DurableWorkflowRunState state, Instant now) {
        assertMutable(state);
        boolean unfinished = state.nodeStates().values().stream().anyMatch(node ->
                node.status() == DurableWorkflowNodeStatus.RUNNING
                        || node.status() == DurableWorkflowNodeStatus.WAITING);
        if (unfinished) throw new IllegalStateException("DURABLE_WORKFLOW_ACTIVE_NODE_EXISTS");
        return copy(state, DurableWorkflowRunStatus.SUCCEEDED, "", state.nodeStates(),
                state.routeHistory(), state.loopCounters(), state.variables(),
                DurableWorkflowWaitState.none(), "", "", now);
    }

    public DurableWorkflowRunState cancel(
            DurableWorkflowRunState state,
            String reason,
            Instant now) {
        if (state.status().terminal()) return state;
        LinkedHashMap<String, DurableWorkflowNodeState> nodes = new LinkedHashMap<>();
        state.nodeStates().forEach((id, node) -> nodes.put(id,
                node.status() == DurableWorkflowNodeStatus.RUNNING
                        || node.status() == DurableWorkflowNodeStatus.WAITING
                        || node.status() == DurableWorkflowNodeStatus.READY
                        ? node.withStatus(DurableWorkflowNodeStatus.CANCELED, node.attempt(),
                        node.outputHash(), "RUN_CANCELED", text(reason),
                        node.startedAt(), now)
                        : node));
        return copy(state, DurableWorkflowRunStatus.CANCELED, "", nodes,
                state.routeHistory(), state.loopCounters(), state.variables(),
                DurableWorkflowWaitState.none(), "RUN_CANCELED", text(reason), now);
    }

    public DurableWorkflowRunState recover(
            BoundWorkflowExecutionPlan plan,
            DurableWorkflowRunState checkpointState,
            Instant now) {
        if (plan == null || checkpointState == null) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_RECOVERY_INPUT_REQUIRED");
        }
        new BoundWorkflowPlanPolicy().assertPlanHash(plan);
        if (!plan.runId().equals(checkpointState.runId())
                || !plan.planHash().equals(checkpointState.planHash())
                || !plan.definitionHash().equals(checkpointState.definitionHash())
                || !plan.contextBundleHash().equals(checkpointState.contextBundleHash())) {
            throw new IllegalStateException("DURABLE_WORKFLOW_RECOVERY_PLAN_MISMATCH");
        }
        if (checkpointState.status() == DurableWorkflowRunStatus.RECOVERY_REVIEW_REQUIRED) {
            throw new IllegalStateException("DURABLE_WORKFLOW_RECOVERY_REVIEW_REQUIRED");
        }
        Map<String, DurableWorkflowNodeState> recoveredNodes = new LinkedHashMap<>();
        checkpointState.nodeStates().forEach((nodeId, node) -> {
            if (node.status() == DurableWorkflowNodeStatus.RUNNING) {
                recoveredNodes.put(nodeId, node.withStatus(
                        DurableWorkflowNodeStatus.READY,
                        Math.max(0, node.attempt() - 1),
                        "",
                        "",
                        "",
                        null,
                        null));
            } else {
                recoveredNodes.put(nodeId, node);
            }
        });
        return copy(checkpointState,
                checkpointState.waitState().active() ? checkpointState.status() : DurableWorkflowRunStatus.RUNNING,
                checkpointState.currentNodeId(), recoveredNodes,
                checkpointState.routeHistory(), checkpointState.loopCounters(),
                checkpointState.variables(), checkpointState.waitState(),
                checkpointState.errorCode(), checkpointState.errorMessage(), now);
    }

    public String toolIdempotencyKey(
            DurableWorkflowRunState state,
            String nodeId,
            int toolCallIndex) {
        DurableWorkflowNodeState node = state.node(nodeId);
        if (node.attempt() <= 0) throw new IllegalStateException("DURABLE_WORKFLOW_NODE_ATTEMPT_REQUIRED");
        if (toolCallIndex < 0) throw new IllegalArgumentException("DURABLE_WORKFLOW_TOOL_CALL_INDEX_INVALID");
        return state.runId() + ":" + nodeId + ":" + node.attempt() + ":" + toolCallIndex;
    }

    private DurableWorkflowRunState copy(
            DurableWorkflowRunState state,
            DurableWorkflowRunStatus status,
            String currentNodeId,
            Map<String, DurableWorkflowNodeState> nodeStates,
            List<DurableWorkflowRouteDecision> routes,
            Map<String, Integer> loops,
            Map<String, Object> variables,
            DurableWorkflowWaitState wait,
            String errorCode,
            String errorMessage,
            Instant now) {
        return new DurableWorkflowRunState(
                state.runId(), state.projectId(), state.planHash(), state.definitionHash(),
                state.contextBundleHash(), status, currentNodeId, nodeStates, routes,
                loops, variables, wait, errorCode, errorMessage, requiredTime(now));
    }

    private Map<String, DurableWorkflowNodeState> replace(
            Map<String, DurableWorkflowNodeState> source,
            String nodeId,
            DurableWorkflowNodeState value) {
        LinkedHashMap<String, DurableWorkflowNodeState> result = new LinkedHashMap<>(source);
        result.put(nodeId, value);
        return result;
    }

    private void assertMutable(DurableWorkflowRunState state) {
        if (state == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_STATE_REQUIRED");
        if (state.status().terminal()) {
            throw new IllegalStateException("DURABLE_WORKFLOW_RUN_TERMINAL:" + state.status());
        }
    }

    private Instant requiredTime(Instant value) {
        if (value == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_TIME_REQUIRED");
        return value;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
