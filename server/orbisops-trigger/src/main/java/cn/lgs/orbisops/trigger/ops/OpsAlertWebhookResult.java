package cn.lgs.orbisops.trigger.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsAlertWebhookResult {

    private Integer receivedAlerts;
    private Integer matchedRules;
    private Integer triggeredRuns;
    private Integer queuedRuns;
    private Integer dedupedAlerts;
    private List<OpsAlertTriggerEvent> events;
}
