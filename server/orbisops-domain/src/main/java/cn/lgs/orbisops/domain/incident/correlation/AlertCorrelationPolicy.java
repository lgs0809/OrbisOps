package cn.lgs.orbisops.domain.incident.correlation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative, automatic episode association. Scores are policy weights, not probabilities.
 * Every new member must match the original anchor; an arbitrary transitive chain cannot
 * swallow unrelated incidents. No rule relies on equal alert titles or product names.
 */
public final class AlertCorrelationPolicy {
    public static final String VERSION = "topology-evidence-v1";
    public static final int MAX_MEMBERS = 32;
    public static final int MAX_CANDIDATES = 100;
    public static final int MAX_TOPOLOGY_EDGES = 256;
    public static final long WINDOW_SECONDS = 900;

    public Decision compare(CorrelationSignal anchor, CorrelationSignal candidate,
                            List<CorrelationTopologyEdge> topology, Set<String> anomalousEntities) {
        if (!anchor.projectId().equals(candidate.projectId()) || anchor.environment().isBlank()
                || !anchor.environment().equals(candidate.environment())) return separate("SCOPE_MISMATCH");
        if (anchor.recovery() || candidate.recovery()) return separate("RECOVERY_IS_NOT_A_NEW_EPISODE");
        if (!validTime(anchor) || !validTime(candidate)) return separate("ONSET_TIME_UNAVAILABLE");
        long delta = Math.abs(Duration.between(anchor.startedAt(), candidate.startedAt()).getSeconds());
        if (delta > WINDOW_SECONDS) return separate("EPISODE_WINDOW_EXCEEDED");
        for (String key : List.of("correlation_id", "trace_id")) {
            if (same(anchor, candidate, key)) return joined(100, "SHARED_" + key.toUpperCase(), List.of("identity:" + key));
        }
        if (delta > 180) return separate("PROPAGATION_WINDOW_EXCEEDED");
        if (same(anchor, candidate, "resource_id")) return joined(90, "SHARED_RESOURCE", List.of("identity:resource_id"));
        if (same(anchor, candidate, "change_id") && relatedEntity(anchor, candidate, topology))
            return joined(90, "SHARED_CHANGE_ON_DEPENDENT_ENTITIES", List.of("identity:change_id"));
        if (!anchor.entityId().isBlank() && anchor.entityId().equals(candidate.entityId())) {
            // A service name alone is too broad to assert one incident.
            if (same(anchor, candidate, "instance") || same(anchor, candidate, "pod"))
                return joined(85, "SAME_SERVICE_INSTANCE", List.of("identity:instance-or-pod"));
            return separate("SAME_SERVICE_WITHOUT_RESOURCE_EVIDENCE");
        }
        List<CorrelationTopologyEdge> edges = usable(anchor, candidate, topology);
        for (CorrelationTopologyEdge edge : edges) {
            if (connects(edge, anchor.entityId(), candidate.entityId()))
                return joined(85, "DEPENDENCY_AND_NEARBY_ONSET", List.of(edge.evidenceRef()));
        }
        // Different symptoms on sibling resources can belong to one propagation episode.
        // Require an anomalous common neighbour, not merely membership in one system.
        Map<String, List<CorrelationTopologyEdge>> neighbours = new HashMap<>();
        for (CorrelationTopologyEdge edge : edges) {
            for (String endpoint : List.of(edge.source(), edge.target()))
                neighbours.computeIfAbsent(endpoint, ignored -> new ArrayList<>()).add(edge);
        }
        Set<String> active = anomalousEntities == null ? Set.of() : anomalousEntities;
        for (Map.Entry<String, List<CorrelationTopologyEdge>> item : neighbours.entrySet()) {
            if (!active.contains(item.getKey()) || item.getValue().size() > 8) continue;
            CorrelationTopologyEdge left = null, right = null;
            for (CorrelationTopologyEdge edge : item.getValue()) {
                if (connects(edge, item.getKey(), anchor.entityId())) left = edge;
                if (connects(edge, item.getKey(), candidate.entityId())) right = edge;
            }
            if (left != null && right != null)
                return joined(85, "ANOMALOUS_COMMON_DEPENDENCY_NEIGHBOUR", List.of(left.evidenceRef(), right.evidenceRef()));
        }
        return separate("INSUFFICIENT_RELATION_EVIDENCE");
    }

    public boolean validTime(CorrelationSignal signal) {
        return signal.startedAt() != null
                && !signal.startedAt().isAfter(signal.receivedAt().plusSeconds(30))
                && !signal.startedAt().isBefore(signal.receivedAt().minus(Duration.ofDays(1)));
    }

    private boolean relatedEntity(CorrelationSignal a, CorrelationSignal b, List<CorrelationTopologyEdge> topology) {
        return !a.entityId().isBlank() && (a.entityId().equals(b.entityId()) || usable(a, b, topology).stream()
                .anyMatch(edge -> connects(edge, a.entityId(), b.entityId())));
    }

    private List<CorrelationTopologyEdge> usable(CorrelationSignal a, CorrelationSignal b, List<CorrelationTopologyEdge> edges) {
        if (edges == null || edges.size() > MAX_TOPOLOGY_EDGES) return List.of();
        return edges.stream().filter(edge -> edge.covers(a.startedAt()) && edge.covers(b.startedAt())).toList();
    }

    private boolean connects(CorrelationTopologyEdge edge, String a, String b) {
        return !a.isBlank() && !b.isBlank() && ((edge.source().equals(a) && edge.target().equals(b))
                || (edge.source().equals(b) && edge.target().equals(a)));
    }

    private boolean same(CorrelationSignal a, CorrelationSignal b, String key) {
        String value = a.identities().getOrDefault(key, "");
        return !value.isBlank() && !Set.of("unknown", "none", "null", "default", "0").contains(value.toLowerCase())
                && value.equals(b.identities().get(key));
    }

    private Decision joined(int score, String reason, List<String> evidence) { return new Decision(true, score, reason, evidence, VERSION); }
    private Decision separate(String reason) { return new Decision(false, 0, reason, List.of(), VERSION); }
    public record Decision(boolean join, int score, String reason, List<String> evidenceRefs, String policyVersion) { }
}
