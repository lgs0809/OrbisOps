package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.application.alert.AlertEventIncidentPort;
import cn.lgs.orbisops.application.incident.IncidentAlertApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.trigger.application.incident.OpsIncidentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpsAlertEventIncidentAdapter implements AlertEventIncidentPort {

    private final IncidentAlertApplicationService incidents;
    private final OpsIncidentMapper incidentMapper;
    private final OpsAlertEventMapper eventMapper;

    public OpsAlertEventIncidentAdapter(
            IncidentAlertApplicationService incidents,
            OpsIncidentMapper incidentMapper,
            OpsAlertEventMapper eventMapper) {
        this.incidents = incidents;
        this.incidentMapper = incidentMapper;
        this.eventMapper = eventMapper;
    }

    @Override
    public void ingest(AlertEventSnapshot event) {
        try {
            incidents.ingest(incidentMapper.alertSignal(eventMapper.view(event)));
        } catch (Exception error) {
            log.warn("告警事件写入 Incident 失败 eventId={}, status={}, error={}",
                    event == null ? null : event.id(),
                    event == null ? null : event.status(),
                    error.getMessage());
        }
    }
}
