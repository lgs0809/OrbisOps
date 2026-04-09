package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.application.alert.AlertAggregationApplicationService;
import cn.lgs.orbisops.application.alert.AlertAggregationTransactionPort;
import cn.lgs.orbisops.application.alert.AlertAuditPort;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.alert.AlertEventIncidentPort;
import cn.lgs.orbisops.application.alert.AlertOutboxApplicationService;
import cn.lgs.orbisops.application.alert.AlertOutboxProjectCapacityPort;
import cn.lgs.orbisops.application.alert.AlertOutboxTransactionPort;
import cn.lgs.orbisops.application.alert.AlertRuleAgentResolverPort;
import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.application.alert.AlertRuleTransactionPort;
import cn.lgs.orbisops.application.alert.AlertTriggerExecutionPort;
import cn.lgs.orbisops.application.alert.AlertTriggerProcessManager;
import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertAggregationRepository;
import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertEventRepository;
import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertOutboxRepository;
import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertRuleRepository;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class OpsAlertApplicationConfiguration {

    @Bean
    public AlertRuleManagementApplicationService alertRuleManagementApplicationService(
            IAlertRuleRepository rules,
            AlertRuleAgentResolverPort agentResolver,
            AlertAuditPort audit,
            AlertRuleTransactionPort transactions) {
        return new AlertRuleManagementApplicationService(rules, agentResolver, audit, transactions);
    }

    @Bean
    public AlertEventApplicationService alertEventApplicationService(
            IAlertEventRepository events,
            AlertEventIncidentPort incidents) {
        return new AlertEventApplicationService(events, incidents);
    }

    @Bean
    public AlertTriggerProcessManager<OpsAlertWebhookResult> alertTriggerProcessManager(
            AlertTriggerExecutionPort<OpsAlertWebhookResult> port) {
        return new AlertTriggerProcessManager<>(port);
    }

    @Bean
    public AlertAggregationApplicationService alertAggregationApplicationService(
            IAlertAggregationRepository aggregates,
            AlertAggregationTransactionPort transactions) {
        return new AlertAggregationApplicationService(
                aggregates,
                () -> UUID.randomUUID().toString().replace("-", ""),
                transactions);
    }

    @Bean
    public AlertOutboxApplicationService alertOutboxApplicationService(
            IAlertOutboxRepository outbox,
            AlertOutboxProjectCapacityPort capacity,
            AlertOutboxTransactionPort transactions) {
        return new AlertOutboxApplicationService(
                outbox,
                () -> UUID.randomUUID().toString().replace("-", ""),
                capacity,
                transactions);
    }
}
