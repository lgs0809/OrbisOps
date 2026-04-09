package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertSignal;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.service.IncidentPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class IncidentAlertApplicationService {

    private static final String SYSTEM_ACTOR = "system";

    private final IIncidentRepository incidents;
    private final IncidentAuditPort audit;
    private final IncidentTransactionPort transactions;
    private final IncidentPolicy policy;

    public IncidentAlertApplicationService(
            IIncidentRepository incidents,
            IncidentAuditPort audit,
            IncidentTransactionPort transactions) {
        if (incidents == null) throw new IllegalArgumentException("INCIDENT_REPOSITORY_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("INCIDENT_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("INCIDENT_TRANSACTION_PORT_REQUIRED");
        this.incidents = incidents;
        this.audit = audit;
        this.transactions = transactions;
        this.policy = new IncidentPolicy();
    }

    public Optional<IncidentSnapshot> ingest(IncidentAlertSignal signal) {
        IncidentAlertDraft draft = policy.alert(signal);
        return transactions.required(() -> {
            Optional<IncidentSnapshot> before = incidents.find(draft.incidentId());
            Optional<IncidentSnapshot> saved = incidents.upsertAlert(draft);
            if (saved.isEmpty()) return Optional.empty();
            IncidentSnapshot current = saved.get();
            if (!draft.recovery()
                    && before.map(IncidentSnapshot::status)
                    .map(status -> status == IncidentStatus.CLOSED || status == IncidentStatus.RESOLVED)
                    .orElse(false)) {
                incidents.appendTimeline(policy.timeline(
                        draft.incidentId(),
                        "INCIDENT_REOPENED",
                        "重复故障重新打开事件",
                        "新的告警 occurrence 已开始；历史 occurrence 的关闭/恢复事实不再决定当前状态。",
                        SYSTEM_ACTOR,
                        "ALERT_EVENT",
                        signal.eventId() == null ? "" : String.valueOf(signal.eventId()),
                        Map.of("dedupKey", draft.projectDedupKey())));
            }
            incidents.appendTimeline(policy.timeline(
                    draft.incidentId(),
                    "ALERT_TRIGGERED",
                    "告警触发分析",
                    draft.summary(),
                    SYSTEM_ACTOR,
                    "ALERT_EVENT",
                    signal.eventId() == null ? "" : String.valueOf(signal.eventId()),
                    draft.metadata()));
            if (!draft.runId().isBlank()) {
                incidents.linkRun(draft.incidentId(), draft.runId());
                incidents.appendTimeline(policy.timeline(
                        draft.incidentId(),
                        "ANALYSIS_LINKED",
                        "关联运维分析",
                        "告警自动提交运维分析 run",
                        SYSTEM_ACTOR,
                        "RUN",
                        draft.runId(),
                        Map.of("runId", draft.runId())));
                current = incidents.find(draft.incidentId()).orElse(current);
            }
            if (draft.recovery()) {
                // A recovery signal is evidence that verification should start; it is
                // not proof that the incident is resolved.
                IncidentStatus target = IncidentStatus.VERIFYING;
                current = incidents.updateStatus(draft.incidentId(), target);
                incidents.appendTimeline(policy.timeline(
                        draft.incidentId(),
                        "RECOVERY_SIGNAL_RECEIVED",
                        "收到恢复信号，等待验证",
                        "Alertmanager 已发送恢复事件；只有验证事实成功后 Incident 才会投影为 RESOLVED。",
                        SYSTEM_ACTOR,
                        "ALERT_EVENT",
                        signal.eventId() == null ? "" : String.valueOf(signal.eventId()),
                        Map.of("status", target.name())));
            }
            IncidentSnapshot result = incidents.find(draft.incidentId()).orElse(current);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("projectId", draft.projectId());
            details.put("dedupKey", draft.projectDedupKey());
            details.put("recovery", draft.recovery());
            if (!draft.runId().isBlank()) details.put("runId", draft.runId());
            audit.record(new IncidentAuditEvent(
                    "alert-upsert",
                    draft.incidentId(),
                    SYSTEM_ACTOR,
                    null,
                    result,
                    details));
            return Optional.of(result);
        });
    }
}
