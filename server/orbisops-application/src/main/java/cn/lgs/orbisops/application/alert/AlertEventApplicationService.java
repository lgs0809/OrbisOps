package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertEventRepository;
import cn.lgs.orbisops.domain.alert.model.AlertEventDraft;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import cn.lgs.orbisops.domain.alert.service.AlertEventPolicy;

import java.util.List;

public final class AlertEventApplicationService {

    private final IAlertEventRepository events;
    private final AlertEventIncidentPort incidents;
    private final AlertEventPolicy policy;

    public AlertEventApplicationService(
            IAlertEventRepository events,
            AlertEventIncidentPort incidents) {
        if (events == null) throw new IllegalArgumentException("ALERT_EVENT_REPOSITORY_REQUIRED");
        if (incidents == null) throw new IllegalArgumentException("ALERT_EVENT_INCIDENT_PORT_REQUIRED");
        this.events = events;
        this.incidents = incidents;
        this.policy = new AlertEventPolicy();
    }

    public List<AlertEventSnapshot> list(int limit) {
        return events.list(policy.limit(limit));
    }

    public AlertEventSnapshot append(AlertEventDraft draft) {
        if (draft == null) throw new IllegalArgumentException("ALERT_EVENT_DRAFT_REQUIRED");
        AlertEventSnapshot event = events.append(draft, policy.terminal(draft.runStatus()));
        if (policy.incidentEligible(event)) {
            incidents.ingest(event);
        }
        return event;
    }

    public void updateRunOutcome(AlertRunOutcome outcome) {
        if (!policy.supports(outcome)) return;
        events.updateRunOutcome(outcome, policy.terminal(outcome.runStatus()));
    }
}
