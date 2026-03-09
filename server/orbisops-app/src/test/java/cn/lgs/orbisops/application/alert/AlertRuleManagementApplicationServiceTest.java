package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertRuleRepository;
import cn.lgs.orbisops.domain.alert.model.AlertAgentResolution;
import cn.lgs.orbisops.domain.alert.model.AlertRuleCandidate;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleManagementApplicationServiceTest {

    @Test
    void createsRuleInsideTransactionAndAuditsActor() {
        FakeRuleRepository repository = new FakeRuleRepository();
        CapturingAudit audit = new CapturingAudit();
        CountingTransactions transactions = new CountingTransactions();
        AlertRuleManagementApplicationService service = service(repository, audit, transactions);

        AlertRuleDefinition saved = service.save(candidate(null, "******"), " alice ");

        assertEquals(1L, saved.id());
        assertEquals("stored-v4", saved.agentDefinitionHash());
        assertEquals(1, transactions.count);
        assertEquals(1, audit.events.size());
        AlertRuleAuditEvent event = audit.events.get(0);
        assertEquals("create", event.action());
        assertEquals("1", event.targetId());
        assertEquals("alice", event.actor());
        assertNull(event.before());
        assertEquals(saved, event.after());
    }

    @Test
    void updatesRuleWithBeforeAfterAndPreservesMaskedSecret() {
        FakeRuleRepository repository = new FakeRuleRepository();
        AlertRuleDefinition before = definition(7L, 1, "stored-secret");
        repository.rules.put(7L, before);
        CapturingAudit audit = new CapturingAudit();
        CountingTransactions transactions = new CountingTransactions();
        AlertRuleManagementApplicationService service = service(repository, audit, transactions);

        AlertRuleDefinition saved = service.save(candidate(7L, "******"), "bob");

        assertEquals("stored-secret", saved.webhookSecret());
        AlertRuleAuditEvent event = audit.events.get(0);
        assertEquals("update", event.action());
        assertEquals(before, event.before());
        assertEquals(saved, event.after());
        assertEquals("bob", event.actor());
        assertEquals(1, transactions.count);
    }

    @Test
    void updatesStatusAndDeletesWithTypedAudits() {
        FakeRuleRepository repository = new FakeRuleRepository();
        repository.rules.put(7L, definition(7L, 1, "secret"));
        CapturingAudit audit = new CapturingAudit();
        CountingTransactions transactions = new CountingTransactions();
        AlertRuleManagementApplicationService service = service(repository, audit, transactions);

        assertTrue(service.updateStatus(7L, 0, "alice"));
        assertEquals(0, repository.rules.get(7L).status());
        assertEquals("status", audit.events.get(0).action());
        assertEquals(1, transactions.count);

        assertTrue(service.delete(7L, "alice"));
        assertEquals("delete", audit.events.get(1).action());
        assertNull(audit.events.get(1).after());
        assertEquals(2, transactions.count);
    }

    @Test
    void rejectsMissingRuleAndDoesNotAuditFailedMutation() {
        FakeRuleRepository repository = new FakeRuleRepository();
        CapturingAudit audit = new CapturingAudit();
        CountingTransactions transactions = new CountingTransactions();
        AlertRuleManagementApplicationService service = service(repository, audit, transactions);

        assertEquals("ALERT_RULE_NOT_FOUND:9",
                assertThrows(IllegalArgumentException.class,
                        () -> service.save(candidate(9L, "new-secret"), "alice")).getMessage());

        repository.rules.put(7L, definition(7L, 1, "secret"));
        repository.failSave = true;
        assertEquals("save failed",
                assertThrows(IllegalStateException.class,
                        () -> service.save(candidate(7L, "new-secret"), "alice")).getMessage());
        assertTrue(audit.events.isEmpty());
        assertEquals(1, transactions.count);
    }

    private AlertRuleManagementApplicationService service(
            FakeRuleRepository repository,
            CapturingAudit audit,
            CountingTransactions transactions) {
        return new AlertRuleManagementApplicationService(
                repository,
                candidate -> new AlertAgentResolution(4, "stored-v4"),
                audit,
                transactions);
    }

    private AlertRuleCandidate candidate(Long id, String secret) {
        return new AlertRuleCandidate(
                id, "Payment Alert", 1, "ALERTMANAGER", "High.*", "critical", "payment",
                Map.of("env", "prod"), "channel-1", "ops", secret, "project-1", "agent-1",
                "LATEST_PUBLISHED", null, "", "question", 30, "5m", true, true,
                5, 120, 20, 300);
    }

    private AlertRuleDefinition definition(Long id, int status, String secret) {
        return new AlertRuleDefinition(
                id, "Payment Alert", status, "ALERTMANAGER", "High.*", "critical", "payment",
                Map.of("env", "prod"), "channel-1", "ops", secret, "project-1", "agent-1",
                "LATEST_PUBLISHED", 4, "stored-v4", "question", 30, "5m", true, true,
                5, 120, 20, 300, "created", "updated");
    }

    private final class FakeRuleRepository implements IAlertRuleRepository {
        private final Map<Long, AlertRuleDefinition> rules = new LinkedHashMap<>();
        private long sequence = 1;
        private boolean failSave;

        @Override
        public List<AlertRuleDefinition> list() {
            return new ArrayList<>(rules.values());
        }

        @Override
        public Optional<AlertRuleDefinition> find(Long id) {
            return Optional.ofNullable(rules.get(id));
        }

        @Override
        public AlertRuleDefinition save(AlertRuleDefinition rule) {
            if (failSave) throw new IllegalStateException("save failed");
            Long id = rule.id() == null ? sequence++ : rule.id();
            AlertRuleDefinition saved = new AlertRuleDefinition(
                    id, rule.ruleName(), rule.status(), rule.sourceType(), rule.alertNameRegex(),
                    rule.severityRegex(), rule.serviceRegex(), rule.matchLabels(), rule.notificationChannelId(),
                    rule.notificationTarget(), rule.webhookSecret(), rule.projectId(), rule.agentDefinitionId(),
                    rule.agentBindingMode(), rule.agentVersion(), rule.agentDefinitionHash(), rule.questionTemplate(),
                    rule.rangeMinutes(), rule.promWindow(), rule.includeRecentLogs(), rule.notifyChannel(),
                    rule.subAgentMaxIterations(), rule.nodeTimeoutSeconds(), rule.maxEvidenceItems(),
                    rule.dedupWindowSeconds(), "created", "updated");
            rules.put(id, saved);
            return saved;
        }

        @Override
        public boolean updateStatus(Long id, int status) {
            AlertRuleDefinition current = rules.get(id);
            if (current == null) return false;
            rules.put(id, new AlertRuleDefinition(
                    current.id(), current.ruleName(), status, current.sourceType(), current.alertNameRegex(),
                    current.severityRegex(), current.serviceRegex(), current.matchLabels(),
                    current.notificationChannelId(), current.notificationTarget(), current.webhookSecret(),
                    current.projectId(), current.agentDefinitionId(), current.agentBindingMode(), current.agentVersion(),
                    current.agentDefinitionHash(), current.questionTemplate(), current.rangeMinutes(), current.promWindow(),
                    current.includeRecentLogs(), current.notifyChannel(), current.subAgentMaxIterations(),
                    current.nodeTimeoutSeconds(), current.maxEvidenceItems(), current.dedupWindowSeconds(),
                    current.createTime(), "updated-2"));
            return true;
        }

        @Override
        public boolean delete(Long id) {
            return rules.remove(id) != null;
        }
    }

    private static final class CapturingAudit implements AlertAuditPort {
        private final List<AlertRuleAuditEvent> events = new ArrayList<>();

        @Override
        public void record(AlertRuleAuditEvent event) {
            events.add(event);
        }
    }

    private static final class CountingTransactions implements AlertRuleTransactionPort {
        private int count;

        @Override
        public <T> T required(Supplier<T> action) {
            count++;
            return action.get();
        }
    }
}
