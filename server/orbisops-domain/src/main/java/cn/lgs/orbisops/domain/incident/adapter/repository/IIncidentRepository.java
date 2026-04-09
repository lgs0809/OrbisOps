package cn.lgs.orbisops.domain.incident.adapter.repository;

import cn.lgs.orbisops.domain.incident.model.IncidentAlertDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentProductMetricsProjection;
import cn.lgs.orbisops.domain.incident.model.IncidentRelation;
import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.domain.incident.model.IncidentWatcher;

import java.util.List;
import java.util.Optional;

public interface IIncidentRepository {

    List<IncidentSnapshot> list(String projectId, IncidentStatus status, int limit);

    Optional<IncidentSnapshot> find(String incidentId);

    IncidentSnapshot create(IncidentDraft draft);

    Optional<IncidentSnapshot> upsertAlert(IncidentAlertDraft draft);

    IncidentSnapshot updateStatus(String incidentId, IncidentStatus status);

    IncidentSnapshot assignOwner(String incidentId, String ownerUserId);

    Optional<IncidentTimelineEntry> appendTimeline(IncidentTimelineDraft draft);

    void linkRun(String incidentId, String runId);

    List<IncidentTimelineEntry> timeline(String incidentId, int limit);

    List<IncidentRunSnapshot> runs(String incidentId);

    List<IncidentWatcher> watchers(String incidentId);

    void addWatcher(String incidentId, String userId, String createdBy);

    void removeWatcher(String incidentId, String userId);

    List<IncidentRelation> relations(String incidentId);

    void addRelation(String incidentId, String relatedIncidentId, String relationType, String createdBy);

    void removeRelation(String incidentId, String relatedIncidentId, String relationType);

    IncidentProductMetricsProjection productMetrics(String helpfulSince);
}
