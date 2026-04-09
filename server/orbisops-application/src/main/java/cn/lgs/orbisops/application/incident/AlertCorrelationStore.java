package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.correlation.AlertCorrelationPolicy.Decision;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationSignal;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationTopologyEdge;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface AlertCorrelationStore {
    <T> T inScope(String projectId, String environment, Supplier<T> action);
    boolean recorded(long eventId);
    List<Group> candidates(CorrelationSignal signal);
    List<CorrelationTopologyEdge> topology(String projectId, String environment);
    void saveTopology(String projectId, String environment, List<CorrelationTopologyEdge> edges, String actor);
    void record(String groupId, CorrelationSignal signal, Decision decision);
    void merge(String sourceGroupId, String targetGroupId, Map<String, Decision> decisions);
    List<Map<String, Object>> groups(String projectId, String environment, int limit);
    List<CorrelationSignal> pending(int limit);
    record Group(String groupId, CorrelationSignal anchor, List<CorrelationSignal> members) { }
}
