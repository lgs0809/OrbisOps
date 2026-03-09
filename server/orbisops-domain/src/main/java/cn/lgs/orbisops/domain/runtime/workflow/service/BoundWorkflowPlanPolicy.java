package cn.lgs.orbisops.domain.runtime.workflow.service;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowRoute;
import cn.lgs.orbisops.domain.runtime.workflow.model.WorkflowResourceDriftPolicy;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Canonical plan hash and explicit resource-drift policy for durable workflow runs. */
public final class BoundWorkflowPlanPolicy {

    public String calculatePlanHash(
            int schemaVersion,
            int definitionVersion,
            String definitionHash,
            String agentId,
            String sessionId,
            String runId,
            String projectId,
            String contextBundleId,
            String contextBundleHash,
            String startNodeId,
            List<BoundWorkflowNode> nodes,
            List<BoundWorkflowRoute> routes,
            List<BoundWorkflowResourceSnapshot> sharedResources,
            List<String> completedStages) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("schemaVersion", schemaVersion);
        canonical.put("definitionVersion", definitionVersion);
        canonical.put("definitionHash", text(definitionHash));
        canonical.put("agentId", text(agentId));
        canonical.put("sessionId", text(sessionId));
        canonical.put("runId", text(runId));
        canonical.put("projectId", text(projectId));
        canonical.put("contextBundleId", text(contextBundleId));
        canonical.put("contextBundleHash", text(contextBundleHash));
        canonical.put("startNodeId", text(startNodeId));
        canonical.put("nodes", nodeTokens(nodes));
        canonical.put("routes", routeTokens(routes));
        canonical.put("sharedResources", resourceTokens(sharedResources));
        canonical.put("completedStages", completedStages == null ? List.of() : List.copyOf(completedStages));
        return fingerprint(canonical);
    }

    public void assertPlanHash(BoundWorkflowExecutionPlan plan) {
        if (plan == null) throw new IllegalArgumentException("BOUND_WORKFLOW_PLAN_REQUIRED");
        String actual = calculatePlanHash(
                plan.schemaVersion(), plan.definitionVersion(), plan.definitionHash(), plan.agentId(),
                plan.sessionId(), plan.runId(), plan.projectId(), plan.contextBundleId(),
                plan.contextBundleHash(), plan.startNodeId(), plan.nodes(), plan.routes(),
                plan.sharedResources(), plan.completedBindingStages());
        if (!actual.equals(plan.planHash())) {
            throw new IllegalStateException("BOUND_WORKFLOW_PLAN_HASH_MISMATCH");
        }
    }

    public void assertResourceCompatibility(
            BoundWorkflowExecutionPlan frozen,
            List<BoundWorkflowResourceSnapshot> current,
            WorkflowResourceDriftPolicy policy) {
        if (frozen == null) throw new IllegalArgumentException("BOUND_WORKFLOW_PLAN_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("WORKFLOW_RESOURCE_DRIFT_POLICY_REQUIRED");
        List<BoundWorkflowResourceSnapshot> expected = frozen.allResources();
        List<BoundWorkflowResourceSnapshot> actual = current == null ? List.of() : current.stream()
                .sorted(Comparator.comparing(BoundWorkflowResourceSnapshot::identityKey))
                .toList();
        if (expected.size() != actual.size()) {
            throw new IllegalStateException("BOUND_WORKFLOW_RESOURCE_DRIFT:COUNT");
        }
        for (int index = 0; index < expected.size(); index++) {
            BoundWorkflowResourceSnapshot left = expected.get(index);
            BoundWorkflowResourceSnapshot right = actual.get(index);
            boolean compatible = policy == WorkflowResourceDriftPolicy.REJECT_ANY_DRIFT
                    ? left.equals(right)
                    : left.immutableFingerprint().equals(right.immutableFingerprint());
            if (!compatible) {
                throw new IllegalStateException("BOUND_WORKFLOW_RESOURCE_DRIFT:" + left.identityKey());
            }
        }
    }

    public String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private List<String> nodeTokens(List<BoundWorkflowNode> nodes) {
        if (nodes == null) return List.of();
        return nodes.stream().sorted(Comparator.comparing(BoundWorkflowNode::nodeId))
                .map(node -> node.nodeId() + "|" + node.nodeType() + "|" + node.publishedType()
                        + "|" + node.compilerId() + "|" + node.configHash()
                        + "|" + String.join(",", resourceTokens(node.resources())))
                .toList();
    }

    private List<String> routeTokens(List<BoundWorkflowRoute> routes) {
        if (routes == null) return List.of();
        return routes.stream()
                .sorted(Comparator.comparingInt(BoundWorkflowRoute::priority).reversed()
                        .thenComparing(BoundWorkflowRoute::edgeId))
                .map(route -> route.edgeId() + "|" + route.fromNodeId() + "|" + route.toNodeId()
                        + "|" + route.routeMode() + "|" + route.ruleHash()
                        + "|" + route.dataMappingHash() + "|" + route.priority()
                        + "|" + route.defaultEdge() + "|" + route.feedbackEdge())
                .toList();
    }

    private List<String> resourceTokens(List<BoundWorkflowResourceSnapshot> resources) {
        if (resources == null) return List.of();
        return resources.stream()
                .sorted(Comparator.comparing(BoundWorkflowResourceSnapshot::identityKey))
                .map(resource -> resource.immutableFingerprint() + ":source=" + resource.bindingSource())
                .toList();
    }

    private String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Character character) return quote(String.valueOf(character));
        if (value instanceof Boolean bool) return bool.toString();
        if (value instanceof Number number) return number(number);
        if (value instanceof Enum<?> enumeration) return quote(enumeration.name());
        if (value instanceof Instant instant) return quote(instant.toString());
        if (value instanceof TemporalAccessor temporal) return quote(temporal.toString());
        if (value instanceof Map<?, ?> map) return canonicalMap(map);
        if (value instanceof Iterable<?> iterable) return canonicalIterable(iterable);
        if (value.getClass().isArray()) return canonicalArray(value);
        throw new IllegalArgumentException("BOUND_WORKFLOW_HASH_VALUE_UNSUPPORTED:" + value.getClass().getName());
    }

    private String canonicalMap(Map<?, ?> source) {
        TreeMap<String, Object> ordered = new TreeMap<>();
        source.forEach((key, value) -> ordered.put(String.valueOf(key), value));
        List<String> entries = new ArrayList<>();
        ordered.forEach((key, value) -> entries.add(quote(key) + ":" + canonical(value)));
        return "{" + String.join(",", entries) + "}";
    }

    private String canonicalIterable(Iterable<?> iterable) {
        List<String> values = new ArrayList<>();
        for (Object value : iterable) values.add(canonical(value));
        return "[" + String.join(",", values) + "]";
    }

    private String canonicalArray(Object array) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < Array.getLength(array); index++) {
            values.add(canonical(Array.get(array, index)));
        }
        return "[" + String.join(",", values) + "]";
    }

    private String number(Number value) {
        if (value instanceof Double number && !Double.isFinite(number)) {
            throw new IllegalArgumentException("BOUND_WORKFLOW_HASH_NUMBER_NON_FINITE");
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            throw new IllegalArgumentException("BOUND_WORKFLOW_HASH_NUMBER_NON_FINITE");
        }
        return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    }

    private String quote(String value) {
        String escaped = value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
