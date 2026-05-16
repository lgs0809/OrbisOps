package cn.lgs.orbisops.trigger.ops;

import java.util.ArrayList;
import java.util.List;

/** Mutable request-scoped counter and response projection helper. */
final class OpsAlertWebhookResultAccumulator {

    private final List<OpsAlertTriggerEvent> events = new ArrayList<>();
    private int matchedRules;
    private int triggeredRuns;
    private int queuedRuns;
    private int dedupedAlerts;

    void matched() {
        matchedRules++;
    }

    void triggered(OpsAlertTriggerEvent event) {
        triggeredRuns++;
        events.add(event);
    }

    void queued(OpsAlertTriggerEvent event) {
        queuedRuns++;
        events.add(event);
    }

    void deduped(OpsAlertTriggerEvent event) {
        dedupedAlerts++;
        events.add(event);
    }

    void event(OpsAlertTriggerEvent event) {
        events.add(event);
    }

    OpsAlertWebhookResult result(int receivedAlerts) {
        return OpsAlertWebhookResult.builder()
                .receivedAlerts(receivedAlerts)
                .matchedRules(matchedRules)
                .triggeredRuns(triggeredRuns)
                .queuedRuns(queuedRuns)
                .dedupedAlerts(dedupedAlerts)
                .events(List.copyOf(events))
                .build();
    }
}
