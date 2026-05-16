package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.alert.AlertAggregationApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertSummaryClaim;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookProtocolService.AlertView;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/** Due aggregation-summary claim, enqueue, acknowledge, and release boundary. */
@Slf4j
public final class OpsAlertSummaryCoordinator {

    private final AlertAggregationApplicationService aggregation;
    private final OpsAlertRuleCatalog rules;
    private final OpsAlertWebhookProtocolService webhookProtocol;
    private final OpsAlertRunSubmissionCoordinator submissions;
    private final OpsAlertTriggerSettings settings;
    private final String sourceType;

    public OpsAlertSummaryCoordinator(
            AlertAggregationApplicationService aggregation,
            OpsAlertRuleCatalog rules,
            OpsAlertWebhookProtocolService webhookProtocol,
            OpsAlertRunSubmissionCoordinator submissions,
            OpsAlertTriggerSettings settings,
            String sourceType) {
        this.aggregation = aggregation;
        this.rules = rules;
        this.webhookProtocol = webhookProtocol;
        this.submissions = submissions;
        this.settings = settings == null ? OpsAlertTriggerSettings.defaults() : settings;
        this.sourceType = sourceType;
    }

    public int enqueueDueSummaries(int limit) {
        List<AlertSummaryClaim> due = aggregation.claimDueSummaries(
                Math.max(1, limit),
                Math.max(30, settings.outboxLockTimeoutSeconds()));
        if (due.isEmpty()) {
            return 0;
        }
        Map<Long, OpsAlertTriggerRule> activeRules = rules.activeByIdForSource(sourceType);
        int queued = 0;
        for (AlertSummaryClaim aggregate : due) {
            try {
                OpsAlertTriggerRule rule = activeRules.get(aggregate.ruleId());
                if (rule == null) {
                    throw new IllegalStateException("ALERT_RULE_NOT_ACTIVE：" + aggregate.ruleId());
                }
                AlertView alert = webhookProtocol.alertView(aggregate.payload());
                submissions.enqueue(
                        rule,
                        alert,
                        aggregate.dispatchKey(),
                        aggregate.aggregateKey(),
                        AlertAggregateEventType.SUMMARY.name(),
                        aggregate.priority(),
                        aggregate.occurrenceCount(),
                        aggregate.payload());
                aggregation.acknowledgeSummary(aggregate, settings.aggregationDebounceSeconds());
                queued++;
            } catch (Exception error) {
                aggregation.releaseSummaryClaim(aggregate);
                log.warn("告警聚合摘要入队失败 aggregateKey={}, error={}",
                        aggregate.aggregateKey(), error.getMessage());
            }
        }
        return queued;
    }
}
