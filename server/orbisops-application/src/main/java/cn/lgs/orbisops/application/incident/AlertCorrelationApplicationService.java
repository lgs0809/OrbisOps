package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.correlation.AlertCorrelationPolicy;
import cn.lgs.orbisops.domain.incident.correlation.AlertCorrelationPolicy.Decision;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationSignal;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationTopologyEdge;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

/** Associates retained alert episodes without pausing their investigation or changing approval facts. */
public final class AlertCorrelationApplicationService {
    private final AlertCorrelationStore store;
    private final AlertCorrelationPolicy policy = new AlertCorrelationPolicy();
    public AlertCorrelationApplicationService(AlertCorrelationStore store) { this.store = store; }

    public void observe(CorrelationSignal signal) {
        store.inScope(signal.projectId(), signal.environment(), () -> {
            if (store.recorded(signal.eventId())) return true;
            List<AlertCorrelationStore.Group> groups = store.candidates(signal);
            List<CorrelationTopologyEdge> edges = store.topology(signal.projectId(), signal.environment());
            Set<String> active = new HashSet<>();
            groups.forEach(group -> group.members().stream().filter(member -> !member.recovery()
                    && policy.validTime(member) && policy.validTime(signal)
                    && Math.abs(java.time.Duration.between(member.startedAt(), signal.startedAt()).getSeconds()) <= 180)
                    .forEach(member -> active.add(member.entityId())));
            active.add(signal.entityId());
            Match selected = null;
            // First replay/recovery of a member stays attached to that occurrence.
            for (AlertCorrelationStore.Group group : groups) {
                if (group.members().stream().anyMatch(member -> member.incidentId().equals(signal.incidentId())
                        && member.startedAt() != null && member.startedAt().equals(signal.startedAt()))) {
                    selected = new Match(group.groupId(), new Decision(true, 100, "EXISTING_OCCURRENCE",
                            List.of("incident:" + signal.incidentId()), AlertCorrelationPolicy.VERSION));
                    break;
                }
                if (group.members().stream().allMatch(CorrelationSignal::recovery)) continue;
                if (group.members().size() >= AlertCorrelationPolicy.MAX_MEMBERS) continue;
                Decision decision = policy.compare(group.anchor(), signal, edges, active);
                if (decision.join() && (selected == null || decision.score() > selected.decision().score()))
                    selected = new Match(group.groupId(), decision);
            }
            if (selected == null) selected = new Match(groupId(signal), new Decision(false, 0,
                    signal.recovery() ? "RECOVERY_WITHOUT_MATCHING_OCCURRENCE" : "INDEPENDENT_INVESTIGATION",
                    List.of(), AlertCorrelationPolicy.VERSION));
            store.record(selected.groupId(), signal, selected.decision());
            if (!"EXISTING_OCCURRENCE".equals(selected.decision().reason())) reassess(signal);
            return true;
        });
    }

    private void reassess(CorrelationSignal signal) {
        // A later upstream alert can explain two earlier, different symptoms. Revisit
        // existing groups automatically; no operator or model call blocks admission.
        List<AlertCorrelationStore.Group> groups = store.candidates(signal);
        List<CorrelationTopologyEdge> edges = store.topology(signal.projectId(), signal.environment());
        Set<String> active = new HashSet<>();
        groups.forEach(group -> group.members().stream().filter(member -> !member.recovery()
                && member.startedAt() != null && signal.startedAt() != null
                && Math.abs(java.time.Duration.between(member.startedAt(), signal.startedAt()).getSeconds()) <= 180)
                .forEach(member -> active.add(member.entityId())));
        Set<String> merged = new HashSet<>();
        for (int i = 0; i < groups.size(); i++) {
            AlertCorrelationStore.Group target = groups.get(i);
            if (merged.contains(target.groupId()) || target.members().stream().anyMatch(CorrelationSignal::recovery)) continue;
            int size = target.members().size();
            for (int j = i + 1; j < groups.size(); j++) {
                AlertCorrelationStore.Group source = groups.get(j);
                if (merged.contains(source.groupId()) || size + source.members().size() > AlertCorrelationPolicy.MAX_MEMBERS) continue;
                Map<String, Decision> decisions = new LinkedHashMap<>();
                for (CorrelationSignal member : source.members()) {
                    Decision decision = policy.compare(target.anchor(), member, edges, active);
                    if (decision.join()) decisions.put(member.incidentId(), decision);
                }
                if (decisions.size() != source.members().size()) continue;
                store.merge(source.groupId(), target.groupId(), decisions);
                merged.add(source.groupId());
                size += source.members().size();
            }
        }
    }

    public int reconcile(int limit) {
        List<CorrelationSignal> signals = store.pending(Math.max(1, Math.min(limit, 200)));
        signals.forEach(this::observe);
        return signals.size();
    }

    public List<Map<String, Object>> groups(String project, String environment, int limit) {
        requireProject(project);
        return store.groups(project, environment == null ? "" : environment.trim(), Math.max(1, Math.min(limit, 100)));
    }

    public List<CorrelationTopologyEdge> topology(String project, String environment) {
        requireScope(project, environment);
        return store.topology(project, environment);
    }

    public void configure(String project, String environment, List<CorrelationTopologyEdge> edges, String actor) {
        requireScope(project, environment);
        if (edges == null || edges.size() > AlertCorrelationPolicy.MAX_TOPOLOGY_EDGES)
            throw new IllegalArgumentException("CORRELATION_TOPOLOGY_LIMIT_EXCEEDED");
        if (edges.stream().map(edge -> edge.source() + "\u0000" + edge.target()).distinct().count() != edges.size())
            throw new IllegalArgumentException("CORRELATION_TOPOLOGY_DUPLICATE_EDGE");
        store.inScope(project, environment, () -> { store.saveTopology(project, environment, edges, actor); return true; });
    }

    private String groupId(CorrelationSignal signal) {
        return "event-group-" + UUID.nameUUIDFromBytes((signal.projectId() + ":" + signal.environment() + ":"
                + signal.incidentId() + ":" + signal.startedAt() + ":" + (signal.startedAt() == null ? signal.eventId() : ""))
                .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
    private void requireProject(String project) {
        if (project == null || project.isBlank() || project.length() > 80)
            throw new IllegalArgumentException("CORRELATION_PROJECT_REQUIRED");
    }
    private void requireScope(String project, String environment) {
        requireProject(project);
        if (environment == null || environment.isBlank() || environment.length() > 80)
            throw new IllegalArgumentException("CORRELATION_ENVIRONMENT_REQUIRED");
    }
    private record Match(String groupId, Decision decision) { }
}
