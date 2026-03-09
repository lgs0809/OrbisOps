package cn.lgs.orbisops.application.alert;

import java.util.Map;

public interface AlertTriggerExecutionPort<W> {
    W handleAlertmanager(String payloadJson,
                         Map<String, Object> payload,
                         String timestamp,
                         String signature);
    AlertTriggerOutboxOutcome processPendingOutbox(int limit);
}
