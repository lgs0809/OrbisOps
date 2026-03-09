package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.application.incident.AppendIncidentTimelineCommand;
import cn.lgs.orbisops.application.incident.CreateIncidentCommand;
import cn.lgs.orbisops.application.incident.IncidentDetailProjection;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertSignal;
import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerEvent;
import cn.lgs.orbisops.trigger.ops.OpsIncident;
import cn.lgs.orbisops.trigger.ops.OpsIncidentTimelineItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsIncidentMapper {

    public CreateIncidentCommand createCommand(Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new CreateIncidentCommand(
                text(safe.get("projectId")),
                text(safe.get("title")),
                text(safe.get("status")),
                text(safe.get("severity")),
                text(safe.get("serviceName")),
                text(safe.get("sourceType")),
                text(safe.get("summary")),
                map(safe.get("labels")),
                map(safe.get("metadata")),
                strings(safe.get("affectedResources")));
    }

    public AppendIncidentTimelineCommand timelineCommand(Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new AppendIncidentTimelineCommand(
                text(safe.get("eventType")),
                text(safe.get("title")),
                text(safe.get("detail")),
                text(safe.get("refType")),
                text(safe.get("refId")),
                map(safe));
    }

    public IncidentAlertSignal alertSignal(OpsAlertTriggerEvent event) {
        if (event == null) throw new IllegalArgumentException("INCIDENT_ALERT_EVENT_REQUIRED");
        return new IncidentAlertSignal(
                event.getId(),
                event.getRuleId(),
                event.getRuleName(),
                event.getProjectId(),
                event.getSourceType(),
                event.getStatus(),
                event.getDedupKey(),
                event.getFingerprint(),
                event.getAlertName(),
                event.getSeverity(),
                event.getServiceName(),
                event.getRunId(),
                event.getFinalSummary(),
                event.getLabelsJson());
    }

    public OpsIncident incident(IncidentSnapshot snapshot) {
        if (snapshot == null) return null;
        return OpsIncident.builder()
                .id(snapshot.id())
                .incidentId(snapshot.incidentId())
                .projectId(snapshot.projectId())
                .title(snapshot.title())
                .status(snapshot.status().name())
                .severity(snapshot.severity())
                .serviceName(snapshot.serviceName())
                .sourceType(snapshot.sourceType())
                .fingerprint(snapshot.fingerprint())
                .dedupKey(snapshot.dedupKey())
                .currentRunId(snapshot.currentRunId())
                .ownerUserId(snapshot.ownerUserId())
                .summary(snapshot.summary())
                .labelsJson(snapshot.labelsJson())
                .metadataJson(snapshot.metadataJson())
                .occurrenceCount(snapshot.occurrenceCount())
                .affectedResourcesJson(snapshot.affectedResourcesJson())
                .firstSeenAt(snapshot.firstSeenAt())
                .lastSeenAt(snapshot.lastSeenAt())
                .createTime(snapshot.createTime())
                .updateTime(snapshot.updateTime())
                .acknowledgedAt(snapshot.acknowledgedAt())
                .resolvedAt(snapshot.resolvedAt())
                .reviewedAt(snapshot.reviewedAt())
                .build();
    }

    public List<OpsIncident> incidents(List<IncidentSnapshot> snapshots) {
        return snapshots == null ? List.of() : snapshots.stream().map(this::incident).toList();
    }

    public OpsIncidentTimelineItem timeline(IncidentTimelineEntry entry) {
        if (entry == null) return null;
        return OpsIncidentTimelineItem.builder()
                .id(entry.id())
                .incidentId(entry.incidentId())
                .eventType(entry.eventType())
                .title(entry.title())
                .detail(entry.detail())
                .actor(entry.actor())
                .refType(entry.refType())
                .refId(entry.refId())
                .payloadJson(entry.payloadJson())
                .createTime(entry.createTime())
                .build();
    }

    public List<OpsIncidentTimelineItem> timeline(List<IncidentTimelineEntry> entries) {
        return entries == null ? List.of() : entries.stream().map(this::timeline).toList();
    }

    public List<Map<String, Object>> runs(List<IncidentRunSnapshot> runs) {
        if (runs == null) return List.of();
        return runs.stream().map(run -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("run_id", run.runId());
            row.put("status", run.status());
            row.put("error_message", run.errorMessage());
            row.put("created_at", run.createdAt());
            row.put("updated_at", run.updatedAt());
            row.put("duration_ms", run.durationMs());
            return row;
        }).toList();
    }

    public Map<String, Object> detail(IncidentDetailProjection projection) {
        if (projection == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("incident", incident(projection.incident()));
        result.put("sourceRefs", projection.sourceRefs());
        result.put("timeline", timeline(projection.timeline()));
        result.put("runs", runs(projection.runs()));
        result.put("changePackages", projection.changePackages());
        result.put("watchers", projection.watchers());
        result.put("relatedIncidents", projection.relatedIncidents());
        result.put("diagnosis", projection.diagnosis());
        result.put("suggestedUserAction", projection.suggestedUserAction());
        return result;
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null && item != null) result.put(String.valueOf(key), item);
        });
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> source)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : source) {
            String normalized = text(item);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
