package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.application.alert.AlertTriggerExecutionPort;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.trigger.application.ops.OpsAnalysisApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerService;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsAlertTriggerExecutionAdapter implements AlertTriggerExecutionPort<OpsAlertWebhookResult> {

    private final OpsAlertTriggerService service;
    private final OpsAnalysisApplicationService analysis;

    public OpsAlertTriggerExecutionAdapter(
            OpsAlertTriggerService service,
            OpsAnalysisApplicationService analysis) {
        this.service = service;
        this.analysis = analysis;
    }

    @Override
    public OpsAlertWebhookResult handleAlertmanager(
            String payloadJson,
            Map<String, Object> payload,
            String timestamp,
            String signature) {
        return service.handleAlertmanager(
                payloadJson, payload, timestamp, signature, analysis::buildAnalysis);
    }

    @Override
    public AlertTriggerOutboxOutcome processPendingOutbox(int limit) {
        return service.processPendingOutbox(limit, analysis::buildAnalysis);
    }
}
