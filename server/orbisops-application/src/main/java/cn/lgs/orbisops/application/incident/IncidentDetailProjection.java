package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.model.DiagnosisResult;
import cn.lgs.orbisops.domain.incident.model.IncidentRelation;
import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.domain.incident.model.IncidentWatcher;

import java.util.List;

/** Product read model for one Incident page. */
public record IncidentDetailProjection(
        IncidentSnapshot incident,
        List<SourceRef> sourceRefs,
        List<IncidentTimelineEntry> timeline,
        List<IncidentRunSnapshot> runs,
        List<ChangeRef> changePackages,
        List<IncidentWatcher> watchers,
        List<RelatedIncidentRef> relatedIncidents,
        DiagnosisResult diagnosis,
        String suggestedUserAction) {

    public IncidentDetailProjection {
        if (incident == null) throw new IllegalArgumentException("INCIDENT_REQUIRED");
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        runs = runs == null ? List.of() : List.copyOf(runs);
        changePackages = changePackages == null ? List.of() : List.copyOf(changePackages);
        watchers = watchers == null ? List.of() : List.copyOf(watchers);
        relatedIncidents = relatedIncidents == null ? List.of() : List.copyOf(relatedIncidents);
        if (diagnosis == null) throw new IllegalArgumentException("INCIDENT_DIAGNOSIS_REQUIRED");
        suggestedUserAction = text(suggestedUserAction);
    }

    public record SourceRef(
            String sourceType,
            String sourceId,
            String title,
            String status,
            String createTime) {
        public SourceRef {
            sourceType = required(sourceType, "INCIDENT_SOURCE_TYPE_REQUIRED");
            sourceId = required(sourceId, "INCIDENT_SOURCE_ID_REQUIRED");
            title = text(title);
            status = text(status);
            createTime = text(createTime);
        }
    }

    public record ChangeRef(
            String packageId,
            String status,
            int version,
            String packageHash,
            String landingRunId,
            String updateTime) {
        public ChangeRef {
            packageId = required(packageId, "INCIDENT_CHANGE_PACKAGE_ID_REQUIRED");
            status = required(status, "INCIDENT_CHANGE_PACKAGE_STATUS_REQUIRED");
            packageHash = text(packageHash);
            landingRunId = text(landingRunId);
            updateTime = text(updateTime);
        }
    }

    public record RelatedIncidentRef(
            String incidentId,
            String title,
            String status,
            String severity,
            String relationType,
            String createTime) {
        public RelatedIncidentRef {
            incidentId = required(incidentId, "INCIDENT_RELATED_ID_REQUIRED");
            title = text(title);
            status = text(status);
            severity = text(severity);
            relationType = text(relationType);
            createTime = text(createTime);
        }
    }

    private static String required(String value, String reason) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reason);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
