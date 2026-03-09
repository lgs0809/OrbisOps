package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertRuleRepository;
import cn.lgs.orbisops.domain.alert.model.AlertAgentResolution;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import cn.lgs.orbisops.domain.alert.service.AlertRuleDefinitionPolicy;

import java.util.List;

public final class AlertRuleManagementApplicationService {

    private final IAlertRuleRepository rules;
    private final AlertRuleAgentResolverPort agentResolver;
    private final AlertAuditPort audit;
    private final AlertRuleTransactionPort transactions;
    private final AlertRuleDefinitionPolicy policy;

    public AlertRuleManagementApplicationService(
            IAlertRuleRepository rules,
            AlertRuleAgentResolverPort agentResolver,
            AlertAuditPort audit,
            AlertRuleTransactionPort transactions) {
        if (rules == null) throw new IllegalArgumentException("ALERT_RULE_REPOSITORY_REQUIRED");
        if (agentResolver == null) throw new IllegalArgumentException("ALERT_RULE_AGENT_RESOLVER_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("ALERT_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("ALERT_RULE_TRANSACTION_REQUIRED");
        this.rules = rules;
        this.agentResolver = agentResolver;
        this.audit = audit;
        this.transactions = transactions;
        this.policy = new AlertRuleDefinitionPolicy();
    }

    public List<AlertRuleDefinition> list() {
        return rules.list();
    }

    public AlertRuleDefinition save(AlertRuleCandidate candidate, String actor) {
        if (candidate == null) throw new IllegalArgumentException("ALERT_RULE_REQUIRED");
        String operator = requiredActor(actor);
        AlertRuleDefinition before = candidate.id() == null
                ? null
                : rules.find(candidate.id()).orElseThrow(() ->
                        new IllegalArgumentException("ALERT_RULE_NOT_FOUND:" + candidate.id()));
        AlertAgentResolution resolution = agentResolver.resolve(candidate);
        AlertRuleDefinition normalized = policy.normalize(
                candidate,
                resolution,
                before == null ? "" : before.webhookSecret());
        return transactions.required(() -> {
            AlertRuleDefinition saved = rules.save(normalized);
            audit.record(new AlertRuleAuditEvent(
                    before == null ? "create" : "update",
                    String.valueOf(saved.id()),
                    before,
                    saved,
                    operator));
            return saved;
        });
    }

    public boolean updateStatus(Long id, Integer status, String actor) {
        Long ruleId = requiredId(id);
        String operator = requiredActor(actor);
        AlertRuleDefinition before = rules.find(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("ALERT_RULE_NOT_FOUND:" + ruleId));
        int normalized = policy.status(status);
        return transactions.required(() -> {
            boolean updated = rules.updateStatus(ruleId, normalized);
            if (!updated) throw new IllegalStateException("ALERT_RULE_STATUS_UPDATE_FAILED:" + ruleId);
            AlertRuleDefinition after = rules.find(ruleId)
                    .orElseThrow(() -> new IllegalStateException("ALERT_RULE_READ_AFTER_UPDATE_FAILED:" + ruleId));
            audit.record(new AlertRuleAuditEvent(
                    "status", String.valueOf(ruleId), before, after, operator));
            return Boolean.TRUE;
        });
    }

    public boolean delete(Long id, String actor) {
        Long ruleId = requiredId(id);
        String operator = requiredActor(actor);
        AlertRuleDefinition before = rules.find(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("ALERT_RULE_NOT_FOUND:" + ruleId));
        return transactions.required(() -> {
            boolean deleted = rules.delete(ruleId);
            if (!deleted) throw new IllegalStateException("ALERT_RULE_DELETE_FAILED:" + ruleId);
            audit.record(new AlertRuleAuditEvent(
                    "delete", String.valueOf(ruleId), before, null, operator));
            return Boolean.TRUE;
        });
    }

    private String requiredActor(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("ALERT_ACTOR_REQUIRED");
        return normalized;
    }

    private Long requiredId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ALERT_RULE_ID_REQUIRED");
        return id;
    }
}
