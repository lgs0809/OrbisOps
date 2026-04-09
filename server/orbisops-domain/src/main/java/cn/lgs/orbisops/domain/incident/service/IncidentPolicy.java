package cn.lgs.orbisops.domain.incident.service;

import cn.lgs.orbisops.domain.incident.model.IncidentAlertDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertSignal;
import cn.lgs.orbisops.domain.incident.model.IncidentDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineDraft;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IncidentPolicy {

    public IncidentDraft manual(
            String incidentId,
            String projectId,
            String title,
            String status,
            String severity,
            String serviceName,
            String sourceType,
            String summary,
            Map<String, Object> labels,
            Map<String, Object> metadata,
            List<String> affectedResources) {
        IncidentStatus normalizedStatus = value(status).isBlank()
                ? IncidentStatus.OPEN
                : IncidentStatus.require(status);
        if (normalizedStatus != IncidentStatus.OPEN) {
            throw new IllegalArgumentException("INCIDENT_CREATE_STATUS_MUST_BE_OPEN");
        }
        return new IncidentDraft(
                incidentId,
                projectId,
                title,
                normalizedStatus,
                severity,
                serviceName,
                sourceType,
                summary,
                labels,
                metadata,
                affectedResources);
    }

    public IncidentAlertDraft alert(IncidentAlertSignal signal) {
        if (signal == null) throw new IllegalArgumentException("INCIDENT_ALERT_SIGNAL_REQUIRED");
        String projectDedupKey = signal.projectId() + ":" + signal.dedupKey();
        String incidentId = "incident_" + UUID.nameUUIDFromBytes(
                        projectDedupKey.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (signal.eventId() != null) metadata.put("alertEventId", signal.eventId());
        if (signal.ruleId() != null) metadata.put("ruleId", signal.ruleId());
        if (!signal.ruleName().isBlank()) metadata.put("ruleName", signal.ruleName());
        metadata.put("projectId", signal.projectId());
        return new IncidentAlertDraft(
                incidentId,
                signal.projectId(),
                signal.alertName(),
                signal.severity(),
                signal.serviceName(),
                signal.sourceType(),
                signal.fingerprint(),
                projectDedupKey,
                signal.runId(),
                signal.finalSummary(),
                signal.labelsJson(),
                metadata,
                signal.serviceName().isBlank() ? List.of() : List.of(signal.serviceName()),
                signal.recovery());
    }

    public IncidentTimelineDraft timeline(
            String incidentId,
            String eventType,
            String title,
            String detail,
            String actor,
            String refType,
            String refId,
            Map<String, Object> payload) {
        return new IncidentTimelineDraft(
                incidentId, eventType, title, detail, actor, refType, refId, payload);
    }

    public int incidentLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    public int timelineLimit(int limit) {
        return Math.max(1, Math.min(limit, 300));
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
