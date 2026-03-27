package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowCheckpoint;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowCheckpointType;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRouteDecision;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitType;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DurableWorkflowCheckpointCodec {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    public DurableWorkflowCheckpoint checkpoint(
            DurableWorkflowCheckpointType type,
            DurableWorkflowRunState state,
            Instant now) {
        Map<String, Object> payload = encodeState(state);
        return new DurableWorkflowCheckpoint(
                type, state, CanonicalObjectHasher.sha256(payload), now);
    }

    public Map<String, Object> encode(DurableWorkflowCheckpoint checkpoint) {
        if (checkpoint == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_CHECKPOINT_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        result.put("checkpointType", checkpoint.type().name());
        result.put("stateHash", checkpoint.stateHash());
        result.put("createdAt", checkpoint.createdAt().toString());
        result.put("state", encodeState(checkpoint.state()));
        return Map.copyOf(result);
    }

    public DurableWorkflowCheckpoint decode(Map<String, Object> payload) {
        Map<String, Object> source = map(payload, "DURABLE_WORKFLOW_CHECKPOINT_PAYLOAD_REQUIRED");
        int schemaVersion = source.containsKey("schemaVersion")
                ? integer(source.get("schemaVersion"))
                : 0;
        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "DURABLE_WORKFLOW_CHECKPOINT_UPCAST_REQUIRED:" + schemaVersion);
        }
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "DURABLE_WORKFLOW_CHECKPOINT_FUTURE_SCHEMA:" + schemaVersion);
        }
        DurableWorkflowCheckpointType type = enumValue(
                DurableWorkflowCheckpointType.class, source.get("checkpointType"),
                "DURABLE_WORKFLOW_CHECKPOINT_TYPE_INVALID");
        DurableWorkflowRunState state = decodeState(map(source.get("state"),
                "DURABLE_WORKFLOW_CHECKPOINT_STATE_REQUIRED"));
        Instant createdAt = instant(source.get("createdAt"), "DURABLE_WORKFLOW_CHECKPOINT_TIME_INVALID");
        String storedHash = text(source.get("stateHash"));
        String actualHash = CanonicalObjectHasher.sha256(encodeState(state));
        if (!actualHash.equals(storedHash)) {
            throw new IllegalStateException("DURABLE_WORKFLOW_CHECKPOINT_HASH_MISMATCH");
        }
        return new DurableWorkflowCheckpoint(type, state, storedHash, createdAt);
    }

    public Map<String, Object> encodeState(DurableWorkflowRunState state) {
        if (state == null) throw new IllegalArgumentException("DURABLE_WORKFLOW_STATE_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", state.runId());
        result.put("projectId", state.projectId());
        result.put("planHash", state.planHash());
        result.put("definitionHash", state.definitionHash());
        result.put("contextBundleHash", state.contextBundleHash());
        result.put("status", state.status().name());
        result.put("currentNodeId", state.currentNodeId());
        result.put("nodeStates", state.nodeStates().values().stream()
                .sorted(Comparator.comparing(DurableWorkflowNodeState::nodeId))
                .map(this::encodeNode).toList());
        result.put("routeHistory", state.routeHistory().stream().map(this::encodeRoute).toList());
        result.put("loopCounters", new TreeMap<>(state.loopCounters()));
        result.put("variables", canonicalVariables(state.variables()));
        result.put("waitState", encodeWait(state.waitState()));
        result.put("errorCode", state.errorCode());
        result.put("errorMessage", state.errorMessage());
        result.put("updatedAt", state.updatedAt().toString());
        return Map.copyOf(result);
    }

    public DurableWorkflowRunState decodeState(Map<String, Object> source) {
        LinkedHashMap<String, DurableWorkflowNodeState> nodes = new LinkedHashMap<>();
        for (Object item : list(source.get("nodeStates"))) {
            DurableWorkflowNodeState node = decodeNode(map(item, "DURABLE_WORKFLOW_NODE_STATE_INVALID"));
            if (nodes.putIfAbsent(node.nodeId(), node) != null) {
                throw new IllegalArgumentException("DURABLE_WORKFLOW_NODE_STATE_DUPLICATE:" + node.nodeId());
            }
        }
        List<DurableWorkflowRouteDecision> routes = new ArrayList<>();
        for (Object item : list(source.get("routeHistory"))) {
            routes.add(decodeRoute(map(item, "DURABLE_WORKFLOW_ROUTE_STATE_INVALID")));
        }
        LinkedHashMap<String, Integer> loops = new LinkedHashMap<>();
        mapOrEmpty(source.get("loopCounters")).forEach((key, value) ->
                loops.put(key, integer(value)));
        return new DurableWorkflowRunState(
                required(source.get("runId"), "DURABLE_WORKFLOW_RUN_ID_REQUIRED"),
                text(source.get("projectId")),
                required(source.get("planHash"), "DURABLE_WORKFLOW_PLAN_HASH_REQUIRED"),
                required(source.get("definitionHash"), "DURABLE_WORKFLOW_DEFINITION_HASH_REQUIRED"),
                required(source.get("contextBundleHash"), "DURABLE_WORKFLOW_CONTEXT_HASH_REQUIRED"),
                enumValue(DurableWorkflowRunStatus.class, source.get("status"),
                        "DURABLE_WORKFLOW_STATUS_INVALID"),
                text(source.get("currentNodeId")), nodes, routes, loops,
                mapOrEmpty(source.get("variables")),
                decodeWait(mapOrEmpty(source.get("waitState"))),
                text(source.get("errorCode")), text(source.get("errorMessage")),
                instant(source.get("updatedAt"), "DURABLE_WORKFLOW_UPDATED_AT_INVALID"));
    }

    private Map<String, Object> canonicalVariables(Map<String, Object> variables) {
        Map<String, Object> source = variables == null ? Map.of() : variables;
        return CanonicalJson.parseObject(CanonicalJson.stringifyPreservingOrder(source));
    }

    private Map<String, Object> encodeNode(DurableWorkflowNodeState node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", node.nodeId());
        result.put("status", node.status().name());
        result.put("attempt", node.attempt());
        result.put("outputHash", node.outputHash());
        result.put("errorCode", node.errorCode());
        result.put("errorMessage", node.errorMessage());
        result.put("startedAt", time(node.startedAt()));
        result.put("finishedAt", time(node.finishedAt()));
        return Map.copyOf(result);
    }

    private DurableWorkflowNodeState decodeNode(Map<String, Object> source) {
        return new DurableWorkflowNodeState(
                required(source.get("nodeId"), "DURABLE_WORKFLOW_NODE_ID_REQUIRED"),
                enumValue(DurableWorkflowNodeStatus.class, source.get("status"),
                        "DURABLE_WORKFLOW_NODE_STATUS_INVALID"),
                integer(source.get("attempt")), text(source.get("outputHash")),
                text(source.get("errorCode")), text(source.get("errorMessage")),
                optionalInstant(source.get("startedAt")), optionalInstant(source.get("finishedAt")));
    }

    private Map<String, Object> encodeRoute(DurableWorkflowRouteDecision route) {
        return Map.of(
                "edgeId", route.edgeId(),
                "fromNodeId", route.fromNodeId(),
                "toNodeId", route.toNodeId(),
                "attempt", route.attempt(),
                "ruleHash", route.ruleHash(),
                "selectedAt", route.selectedAt().toString());
    }

    private DurableWorkflowRouteDecision decodeRoute(Map<String, Object> source) {
        return new DurableWorkflowRouteDecision(
                required(source.get("edgeId"), "DURABLE_WORKFLOW_ROUTE_ID_REQUIRED"),
                required(source.get("fromNodeId"), "DURABLE_WORKFLOW_ROUTE_FROM_REQUIRED"),
                required(source.get("toNodeId"), "DURABLE_WORKFLOW_ROUTE_TO_REQUIRED"),
                integer(source.get("attempt")),
                required(source.get("ruleHash"), "DURABLE_WORKFLOW_ROUTE_RULE_HASH_REQUIRED"),
                instant(source.get("selectedAt"), "DURABLE_WORKFLOW_ROUTE_TIME_INVALID"));
    }

    private Map<String, Object> encodeWait(DurableWorkflowWaitState wait) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", wait.type().name());
        result.put("nodeId", wait.nodeId());
        result.put("tokenHash", wait.tokenHash());
        result.put("requestedAt", time(wait.requestedAt()));
        result.put("expiresAt", time(wait.expiresAt()));
        result.put("resumedAt", time(wait.resumedAt()));
        result.put("decision", wait.decision());
        return Map.copyOf(result);
    }

    private DurableWorkflowWaitState decodeWait(Map<String, Object> source) {
        if (source.isEmpty()) return DurableWorkflowWaitState.none();
        return new DurableWorkflowWaitState(
                enumValue(DurableWorkflowWaitType.class, source.get("type"),
                        "DURABLE_WORKFLOW_WAIT_TYPE_INVALID"),
                text(source.get("nodeId")), text(source.get("tokenHash")),
                optionalInstant(source.get("requestedAt")), optionalInstant(source.get("expiresAt")),
                optionalInstant(source.get("resumedAt")), text(source.get("decision")));
    }

    private Map<String, Object> map(Object value, String reasonCode) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(reasonCode);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> mapOrEmpty(Object value) {
        if (value == null) return Map.of();
        return map(value, "DURABLE_WORKFLOW_MAP_INVALID");
    }

    private List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_NUMBER_INVALID");
        }
    }

    private Instant instant(Object value, String reasonCode) {
        Instant parsed = optionalInstant(value);
        if (parsed == null) throw new IllegalArgumentException(reasonCode);
        return parsed;
    }

    private Instant optionalInstant(Object value) {
        String text = text(value);
        if (text.isBlank()) return null;
        try {
            return Instant.parse(text);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("DURABLE_WORKFLOW_TIME_INVALID", error);
        }
    }

    private <E extends Enum<E>> E enumValue(
            Class<E> type,
            Object value,
            String reasonCode) {
        try {
            return Enum.valueOf(type, required(value, reasonCode));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(reasonCode, error);
        }
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String time(Instant value) {
        return value == null ? "" : value.toString();
    }
}
