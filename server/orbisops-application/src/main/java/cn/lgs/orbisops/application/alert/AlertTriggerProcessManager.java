package cn.lgs.orbisops.application.alert;

import java.util.Map;

public final class AlertTriggerProcessManager<W> {

    private final AlertTriggerExecutionPort<W> port;

    public AlertTriggerProcessManager(AlertTriggerExecutionPort<W> port) {
        if (port == null) throw new IllegalArgumentException("ALERT_TRIGGER_EXECUTION_PORT_REQUIRED");
        this.port = port;
    }

    public W receiveAlertmanager(String payloadJson,
                                 Map<String, Object> payload,
                                 String timestamp,
                                 String signature) {
        return port.handleAlertmanager(payloadJson,
                payload == null ? Map.of() : Map.copyOf(payload), value(timestamp), value(signature));
    }

    public AlertTriggerOutboxOutcome processOutbox(int limit) {
        return port.processPendingOutbox(Math.max(1, Math.min(limit, 200)));
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
