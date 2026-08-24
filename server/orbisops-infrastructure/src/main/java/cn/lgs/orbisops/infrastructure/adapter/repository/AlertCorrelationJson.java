package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.incident.correlation.CorrelationSignal;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationTopologyEdge;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AlertCorrelationJson {
    private AlertCorrelationJson() { }
    static String signal(CorrelationSignal s) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("eventId", s.eventId()); values.put("incidentId", s.incidentId());
        values.put("projectId", s.projectId()); values.put("environment", s.environment());
        values.put("entityId", s.entityId()); values.put("startedAt", s.startedAt() == null ? null : s.startedAt().toString());
        values.put("receivedAt", s.receivedAt().toString()); values.put("identities", s.identities());
        values.put("title", s.title()); values.put("recovery", s.recovery());
        return JSON.toJSONString(values);
    }
    static CorrelationSignal signal(String json) {
        JSONObject value = JSON.parseObject(json);
        Map<String, String> identities = new LinkedHashMap<>();
        value.getJSONObject("identities").forEach((key, item) -> identities.put(key, String.valueOf(item)));
        return new CorrelationSignal(value.getLongValue("eventId"), value.getString("incidentId"), value.getString("projectId"),
                value.getString("environment"), value.getString("entityId"), instant(value.getString("startedAt")),
                instant(value.getString("receivedAt")), identities, value.getString("title"), value.getBooleanValue("recovery"));
    }
    static String edges(List<CorrelationTopologyEdge> edges) {
        return JSON.toJSONString(edges.stream().map(edge -> Map.of("source", edge.source(), "target", edge.target(),
                "evidenceRef", edge.evidenceRef(), "observedAt", edge.observedAt().toString(), "expiresAt", edge.expiresAt().toString())).toList());
    }
    static List<CorrelationTopologyEdge> edges(String json) {
        return JSON.parseArray(json).stream().map(item -> {
            JSONObject value = (JSONObject) item;
            return new CorrelationTopologyEdge(value.getString("source"), value.getString("target"), value.getString("evidenceRef"),
                    instant(value.getString("observedAt")), instant(value.getString("expiresAt")));
        }).toList();
    }
    static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (RuntimeException ignored) { return null; }
    }
}
