package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertAggregationApplicationService;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.alert.AlertOutboxApplicationService;
import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationDecision;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertEventMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertOutboxMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;
import cn.lgs.orbisops.trigger.ops.OpsAlertRunSubmissionCoordinator.SubmissionOutcome;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookProtocolService.AlertView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Alertmanager ingress facade over protocol, aggregation, persistence, and durable submission boundaries. */
@Slf4j
@Service
public class OpsAlertTriggerService {

    static final String SOURCE_ALERTMANAGER = "ALERTMANAGER";

    private final AlertAggregationApplicationService aggregation;
    private final OpsAlertWebhookProtocolService webhookProtocol;
    private final OpsAlertRuleCatalog rules;
    private final OpsAlertEventRecorder events;
    private final OpsAlertRunSubmissionCoordinator submissions;
    private final OpsAlertSummaryCoordinator summaries;
    private final OpsAlertTriggerSettings settings;

    public OpsAlertTriggerService(
            OpsAnalysisRunService analysisRuns,
            AlertRuleManagementApplicationService alertRules,
            AlertEventApplicationService alertEvents,
            AlertAggregationApplicationService aggregation,
            AlertOutboxApplicationService alertOutbox,
            OpsAlertRuleMapper alertRuleMapper,
            OpsAlertEventMapper alertEventMapper,
            OpsAlertOutboxMapper alertOutboxMapper) {
        this(
                analysisRuns,
                alertRules,
                alertEvents,
                aggregation,
                alertOutbox,
                alertRuleMapper,
                alertEventMapper,
                alertOutboxMapper,
                null,
                OpsAlertTriggerSettings.defaults());
    }

    @Autowired
    public OpsAlertTriggerService(
            OpsAnalysisRunService analysisRuns,
            AlertRuleManagementApplicationService alertRules,
            AlertEventApplicationService alertEvents,
            AlertAggregationApplicationService aggregation,
            AlertOutboxApplicationService alertOutbox,
            OpsAlertRuleMapper alertRuleMapper,
            OpsAlertEventMapper alertEventMapper,
            OpsAlertOutboxMapper alertOutboxMapper,
            OpsAgentDefinitionQueryGateway agentDefinitions,
            OpsAlertTriggerSettings settings) {
        OpsAlertWebhookProtocolService protocol = new OpsAlertWebhookProtocolService();
        OpsAlertRuleCatalog ruleCatalog = new OpsAlertRuleCatalog(alertRules, alertRuleMapper);
        OpsAlertRunSubmissionCoordinator submissionCoordinator = new OpsAlertRunSubmissionCoordinator(
                analysisRuns,
                alertOutbox,
                alertOutboxMapper,
                new OpsAlertAnalysisRequestFactory(),
                agentDefinitions,
                settings,
                SOURCE_ALERTMANAGER);
        this.aggregation = aggregation;
        this.webhookProtocol = protocol;
        this.rules = ruleCatalog;
        this.events = new OpsAlertEventRecorder(alertEvents, alertEventMapper, SOURCE_ALERTMANAGER);
        this.submissions = submissionCoordinator;
        this.summaries = new OpsAlertSummaryCoordinator(
                aggregation,
                ruleCatalog,
                protocol,
                submissionCoordinator,
                settings,
                SOURCE_ALERTMANAGER);
        this.settings = settings == null ? OpsAlertTriggerSettings.defaults() : settings;
    }

    OpsAlertTriggerService(
            AlertAggregationApplicationService aggregation,
            OpsAlertWebhookProtocolService webhookProtocol,
            OpsAlertRuleCatalog rules,
            OpsAlertEventRecorder events,
            OpsAlertRunSubmissionCoordinator submissions,
            OpsAlertSummaryCoordinator summaries,
            OpsAlertTriggerSettings settings) {
        this.aggregation = aggregation;
        this.webhookProtocol = webhookProtocol;
        this.rules = rules;
        this.events = events;
        this.submissions = submissions;
        this.summaries = summaries;
        this.settings = settings == null ? OpsAlertTriggerSettings.defaults() : settings;
    }

    public OpsAlertWebhookResult handleAlertmanager(
            String payloadJson,
            Map<String, Object> payload,
            String timestamp,
            String signature,
            Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analyzer) {
        List<Map<String, Object>> incoming = webhookProtocol.extractAlerts(payload);
        List<OpsAlertTriggerRule> activeRules = rules.activeForSource(SOURCE_ALERTMANAGER);
        OpsAlertWebhookResultAccumulator result = new OpsAlertWebhookResultAccumulator();
        for (Map<String, Object> rawAlert : incoming) {
            AlertView alert = webhookProtocol.alertView(rawAlert);
            for (OpsAlertTriggerRule rule : activeRules) {
                if (!webhookProtocol.matches(rule, alert)) {
                    continue;
                }
                result.matched();
                if (!webhookProtocol.verifySignature(
                        rule,
                        payloadJson,
                        timestamp,
                        signature,
                        settings.signatureMaxSkewSeconds())) {
                    result.event(events.record(
                            rule,
                            alert,
                            webhookProtocol.dedupKey(SOURCE_ALERTMANAGER, rule, alert.fingerprint()),
                            "REJECTED",
                            null,
                            "FAILED",
                            null,
                            "Webhook 签名校验失败。",
                            rawAlert));
                    continue;
                }
                handleMatched(rule, alert, rawAlert, analyzer, result);
            }
        }
        return result.result(incoming.size());
    }

    public AlertTriggerOutboxOutcome processPendingOutbox(
            int limit,
            Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analyzer) {
        int summariesQueued = summaries.enqueueDueSummaries(Math.max(1, limit));
        return submissions.processPending(limit, summariesQueued, analyzer);
    }

    private void handleMatched(
            OpsAlertTriggerRule rule,
            AlertView alert,
            Map<String, Object> rawAlert,
            Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analyzer,
            OpsAlertWebhookResultAccumulator result) {
        AlertAggregationDecision decision = aggregation.record(
                rule.getProjectId(),
                Optional.ofNullable(rule.getId()).orElse(0L),
                alert.fingerprint(),
                alert.status(),
                alert.severity(),
                alert.service(),
                rawAlert,
                settings.aggregationDebounceSeconds(),
                settings.aggregationMaxWaitSeconds());
        if (!decision.dispatchNow()) {
            result.deduped(events.record(
                    rule,
                    alert,
                    decision.aggregateKey(),
                    "DEDUPED",
                    null,
                    null,
                    null,
                    decision.eventType() == AlertAggregateEventType.IGNORED_RECOVERY
                            ? "未观察到活动告警，恢复事件仅记录不启动分析。"
                            : "重复告警已聚合，将在 debounce/max-wait 到期后生成摘要分析。",
                    rawAlert));
            return;
        }
        try {
            SubmissionOutcome outcome = submissions.enqueueAndDispatch(
                    rule, alert, decision, rawAlert, analyzer);
            if (outcome.triggered()) {
                result.triggered(events.record(
                        rule,
                        alert,
                        decision.aggregateKey(),
                        decision.eventType() == AlertAggregateEventType.RECOVERY
                                ? "RECOVERY_TRIGGERED" : "TRIGGERED",
                        outcome.runId(),
                        "PENDING",
                        null,
                        null,
                        rawAlert));
            } else {
                result.queued(events.record(
                        rule,
                        alert,
                        decision.aggregateKey(),
                        decision.eventType() == AlertAggregateEventType.RECOVERY
                                ? "RECOVERY_QUEUED" : "QUEUED",
                        null,
                        "PENDING",
                        null,
                        "项目并发配额已满，告警按优先级进入有界队列。",
                        rawAlert));
            }
        } catch (Exception error) {
            log.warn("告警触发运维分析失败 ruleId={}, fingerprint={}, error={}",
                    rule.getId(), alert.fingerprint(), error.getMessage());
            result.event(events.record(
                    rule,
                    alert,
                    decision.aggregateKey(),
                    "FAILED",
                    null,
                    "FAILED",
                    null,
                    error.getMessage(),
                    rawAlert));
        }
    }
}
