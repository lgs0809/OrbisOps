package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;

import java.util.List;
import java.util.Map;

public interface AnalysisTaskSupplementPort {

    List<GraphEvent> events(String runId, int limit);

    List<Map<String, Object>> evidence(String projectId, String runId, int limit);

    List<Map<String, Object>> toolResults(String projectId, String runId, int limit);

    List<Map<String, Object>> changePackages(String projectId, String sessionId, int limit);

    List<Map<String, Object>> skillUsages(String projectId, String runId);
}
