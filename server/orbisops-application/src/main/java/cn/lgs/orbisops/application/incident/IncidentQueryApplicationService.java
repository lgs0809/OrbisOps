package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.model.DiagnosisResult;
import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.domain.incident.model.IncidentProductMetricsProjection;
import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.domain.incident.service.IncidentPolicy;
import cn.lgs.orbisops.domain.incident.service.IncidentStatusProjectionPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds the Incident business read model from existing execution facts. */
public final class IncidentQueryApplicationService {

    private final IIncidentRepository incidents;
    private final IncidentChangePackageQueryPort changePackages;
    private final IncidentDiagnosisQueryPort diagnoses;
    private final IncidentPolicy policy;
    private final IncidentStatusProjectionPolicy statusProjection;

    public IncidentQueryApplicationService(IIncidentRepository incidents) {
        this(incidents, null, null);
    }

    public IncidentQueryApplicationService(
            IIncidentRepository incidents,
            IncidentChangePackageQueryPort changePackages) {
        this(incidents, changePackages, null);
    }

    public IncidentQueryApplicationService(
            IIncidentRepository incidents,
            IncidentChangePackageQueryPort changePackages,
            IncidentDiagnosisQueryPort diagnoses) {
        if (incidents == null) throw new IllegalArgumentException("INCIDENT_REPOSITORY_REQUIRED");
        this.incidents = incidents;
        this.changePackages = changePackages;
        this.diagnoses = diagnoses;
        this.policy = new IncidentPolicy();
        this.statusProjection = new IncidentStatusProjectionPolicy();
    }

    public List<IncidentSnapshot> list(String projectId, String status, int limit) {
        IncidentStatus expected = value(status).isBlank() ? null : IncidentStatus.require(status);
        int bounded = policy.incidentLimit(limit);
        // Status is a projection over facts, so filtering the legacy persisted column first
        // would incorrectly hide Incident rows whose facts moved them forward.
        return incidents.list(value(projectId), null, Math.max(bounded, 200)).stream()
                .map(this::projectStatus)
                .filter(incident -> expected == null || incident.status() == expected)
                .limit(bounded)
                .toList();
    }

    public Optional<IncidentSnapshot> get(String incidentId) {
        return incidents.find(required(incidentId, "INCIDENT_ID_REQUIRED"))
                .map(this::projectStatus);
    }

    public Optional<IncidentDetailProjection> detail(String incidentId) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        return incidents.find(id).map(raw -> {
            List<IncidentTimelineEntry> timeline = incidents.timeline(id, policy.timelineLimit(200));
            List<IncidentRunSnapshot> runs = incidents.runs(id);
            List<IncidentChangePackageSnapshot> changes = changes(id);
            IncidentSnapshot incident = withStatus(raw, statusProjection.project(
                    raw,
                    timeline,
                    runs,
                    changes.stream().map(change -> change.status()).toList()));
            List<IncidentDetailProjection.SourceRef> sources = sourceRefs(incident, timeline);
            List<IncidentDetailProjection.ChangeRef> changeRefs = changes.stream()
                    .map(this::changeRef)
                    .toList();
            var watchers = incidents.watchers(id);
            List<IncidentDetailProjection.RelatedIncidentRef> related = incidents.relations(id).stream()
                    .map(relation -> relatedIncident(relation.relatedIncidentId(), relation.relationType(), relation.createTime()))
                    .flatMap(Optional::stream)
                    .toList();
            DiagnosisResult diagnosis = diagnosis(incident, timeline, runs, changes);
            return new IncidentDetailProjection(
                    incident,
                    sources,
                    timeline,
                    runs,
                    changeRefs,
                    watchers,
                    related,
                    diagnosis,
                    nextAction(incident.status()));
        });
    }

    public List<IncidentTimelineEntry> timeline(String incidentId, int limit) {
        return incidents.timeline(
                required(incidentId, "INCIDENT_ID_REQUIRED"),
                policy.timelineLimit(limit));
    }

    public List<IncidentRunSnapshot> runs(String incidentId) {
        return incidents.runs(required(incidentId, "INCIDENT_ID_REQUIRED"));
    }

    public List<cn.lgs.orbisops.domain.incident.model.IncidentWatcher> watchers(String incidentId) {
        return incidents.watchers(required(incidentId, "INCIDENT_ID_REQUIRED"));
    }

    public List<IncidentDetailProjection.RelatedIncidentRef> relatedIncidents(String incidentId) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        return incidents.relations(id).stream()
                .map(relation -> relatedIncident(relation.relatedIncidentId(), relation.relationType(), relation.createTime()))
                .flatMap(Optional::stream)
                .toList();
    }

    public IncidentProductMetricsProjection productMetrics(String helpfulSince) {
        return incidents.productMetrics(required(helpfulSince, "INCIDENT_METRICS_SINCE_REQUIRED"));
    }

    private IncidentSnapshot projectStatus(IncidentSnapshot raw) {
        List<IncidentTimelineEntry> timeline = incidents.timeline(raw.incidentId(), 100);
        List<IncidentRunSnapshot> runs = incidents.runs(raw.incidentId());
        List<IncidentChangePackageSnapshot> changes = changes(raw.incidentId());
        IncidentStatus projected = statusProjection.project(
                raw,
                timeline,
                runs,
                changes.stream().map(change -> change.status()).toList());
        return withStatus(raw, projected);
    }

    private List<IncidentChangePackageSnapshot> changes(String incidentId) {
        if (changePackages == null) return List.of();
        return changePackages.findByIncidentId(incidentId, 100);
    }

    private List<IncidentDetailProjection.SourceRef> sourceRefs(
            IncidentSnapshot incident,
            List<IncidentTimelineEntry> timeline) {
        Map<String, IncidentDetailProjection.SourceRef> unique = new LinkedHashMap<>();
        String rootType = value(incident.sourceType()).isBlank() ? "MANUAL" : incident.sourceType();
        unique.put(rootType + ":" + incident.incidentId(), new IncidentDetailProjection.SourceRef(
                rootType,
                incident.incidentId(),
                incident.title(),
                "SOURCE",
                incident.createTime()));
        for (IncidentTimelineEntry entry : timeline) {
            if (entry == null || value(entry.refType()).isBlank() || value(entry.refId()).isBlank()) continue;
            String key = entry.refType() + ":" + entry.refId();
            unique.putIfAbsent(key, new IncidentDetailProjection.SourceRef(
                    entry.refType(),
                    entry.refId(),
                    entry.title(),
                    entry.eventType(),
                    entry.createTime()));
        }
        return List.copyOf(unique.values());
    }

    private Optional<IncidentDetailProjection.RelatedIncidentRef> relatedIncident(
            String relatedIncidentId,
            String relationType,
            String relationCreateTime) {
        return incidents.find(relatedIncidentId)
                .map(this::projectStatus)
                .map(related -> new IncidentDetailProjection.RelatedIncidentRef(
                        related.incidentId(),
                        related.title(),
                        related.status().name(),
                        related.severity(),
                        relationType,
                        relationCreateTime));
    }

    private IncidentDetailProjection.ChangeRef changeRef(IncidentChangePackageSnapshot change) {
        return new IncidentDetailProjection.ChangeRef(
                change.packageId(),
                change.status(),
                change.version(),
                change.packageHash(),
                change.landingRunId(),
                change.updateTime());
    }

    private DiagnosisResult diagnosis(
            IncidentSnapshot incident,
            List<IncidentTimelineEntry> timeline,
            List<IncidentRunSnapshot> runs,
            List<IncidentChangePackageSnapshot> changes) {
        if (diagnoses != null && !runs.isEmpty()) {
            Optional<DiagnosisResult> structured = diagnoses.latestByRunIds(
                    runs.stream()
                            .map(IncidentRunSnapshot::runId)
                            .filter(runId -> !value(runId).isBlank())
                            .toList());
            if (structured.isPresent()) {
                return structured.get();
            }
        }
        boolean hasInvestigation = !runs.isEmpty()
                || timeline.stream().anyMatch(item -> "ANALYSIS_LINKED".equalsIgnoreCase(item.eventType()));
        boolean requiresAction = incident.status() == IncidentStatus.ACTION_REQUIRED
                || incident.status() == IncidentStatus.REMEDIATING;
        String unknown = hasInvestigation
                ? "当前运行记录尚未提供符合统一 Diagnosis 契约的 evidenceRef/resultId/outputHash，不能把自由文本摘要冒充已验证事实。"
                : "尚未形成带证据引用的结构化诊断。";
        String summary = value(incident.summary()).isBlank()
                ? "事件已建立，等待基于真实数据源完成诊断。"
                : incident.summary();
        return DiagnosisResult.insufficient(summary, unknown, requiresAction, nextAction(incident.status()));
    }

    private String nextAction(IncidentStatus status) {
        return switch (status) {
            case OPEN -> "开始智能诊断";
            case INVESTIGATING -> "查看 Evidence Timeline";
            case ACTION_REQUIRED -> "前往执行中心审阅 ChangePackage";
            case REMEDIATING -> "查看执行中心处置进度";
            case VERIFYING -> "执行恢复验证";
            case RESOLVED -> "确认结果并关闭事件";
            case CLOSED -> "无需操作";
        };
    }

    private IncidentSnapshot withStatus(IncidentSnapshot source, IncidentStatus status) {
        return new IncidentSnapshot(
                source.id(), source.incidentId(), source.projectId(), source.title(), status,
                source.severity(), source.serviceName(), source.sourceType(), source.fingerprint(),
                source.dedupKey(), source.currentRunId(), source.ownerUserId(), source.summary(), source.labelsJson(),
                source.metadataJson(), source.occurrenceCount(), source.affectedResourcesJson(),
                source.firstSeenAt(), source.lastSeenAt(), source.createTime(), source.updateTime(),
                source.acknowledgedAt(), source.resolvedAt(), source.reviewedAt());
    }

    private String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
