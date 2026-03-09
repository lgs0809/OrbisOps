package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertEventMapper;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookProtocolService.AlertView;

import java.util.Map;

/** Alert event persistence and public view projection boundary. */
public final class OpsAlertEventRecorder {

    private final AlertEventApplicationService alertEvents;
    private final OpsAlertEventMapper mapper;
    private final String sourceType;

    public OpsAlertEventRecorder(
            AlertEventApplicationService alertEvents,
            OpsAlertEventMapper mapper,
            String sourceType) {
        this.alertEvents = alertEvents;
        this.mapper = mapper;
        this.sourceType = sourceType;
    }

    public OpsAlertTriggerEvent record(
            OpsAlertTriggerRule rule,
            AlertView alert,
            String dispatchKey,
            String status,
            String runId,
            String runStatus,
            String finalSummary,
            String error,
            Map<String, Object> rawAlert) {
        return mapper.view(alertEvents.append(mapper.draft(
                rule,
                sourceType,
                status,
                dispatchKey,
                alert.fingerprint(),
                alert.alertName(),
                alert.severity(),
                alert.service(),
                runId,
                runStatus,
                finalSummary,
                error,
                alert.labels(),
                alert.annotations(),
                rawAlert)));
    }
}
