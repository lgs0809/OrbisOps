package cn.lgs.orbisops.application.audit;

import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditPolicyRepository;
import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditRepository;
import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.AuditPolicyStatus;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditCriteria;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditDraft;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditReadiness;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAuditApplicationServiceTest {

    @Test
    void recordsTypedDraftWithRiskAndDefaults() {
        FakeAuditRepository audits = new FakeAuditRepository();
        FakePolicyRepository policies = new FakePolicyRepository(false);
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(audits, policies);

        ConfigAuditEntry entry = service.record(new ConfigAuditCommand(
                "project-1", "agent-1", "skill", "delete", "", "skill-1", "", "",
                "u1", "alice", "admin", "127.0.0.1", "trace-1", "{}", "{}"));

        assertFalse(entry.auditId().isBlank());
        assertEquals("skill", entry.targetType());
        assertEquals("HIGH", entry.riskLevel());
        assertEquals("SUCCESS", entry.resultStatus());
        assertEquals("alice", entry.operatorName());
        assertEquals(1, audits.entries.size());
    }

    @Test
    void repeatedDeliveryKeyMustReuseSameAuditIdentity() {
        FakeAuditRepository audits = new FakeAuditRepository();
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(
                audits, new FakePolicyRepository(false));
        ConfigAuditCommand command = new ConfigAuditCommand(
                "project-1", "agent-1", "tool-execution", "completed", "tool", "tool-1", "", "",
                "u1", "alice", "admin", "127.0.0.1", "trace-1", "{}", "{}");

        ConfigAuditEntry first = service.recordIdempotent(command, "tool-completion-audit:projection-1");
        ConfigAuditEntry second = service.recordIdempotent(command, "tool-completion-audit:projection-1");

        assertEquals(first.auditId(), second.auditId());
        assertEquals(1, audits.entries.size());
    }

    @Test
    void searchClampsLimitToFiveHundred() {
        FakeAuditRepository audits = new FakeAuditRepository();
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(
                audits, new FakePolicyRepository(false));

        service.search(new AuditQuery("project-1", "", "", "", "", "", "", "", 1000));

        assertEquals(500, audits.criteria.limit());
    }

    @Test
    void delegatesDetailOperatorAndReadiness() {
        FakeAuditRepository audits = new FakeAuditRepository();
        ConfigAuditEntry entry = audits.append(draft("audit-1"));
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(
                audits, new FakePolicyRepository(false));

        assertEquals(entry, service.detail("audit-1").orElseThrow());
        assertEquals(List.of(entry), service.listForOperator("alice", 999));
        assertEquals(200, audits.operatorLimit);
        assertEquals("UP", service.readiness().status());
    }

    @Test
    void suppliesTypedDefaultPolicyAndRejectsNonPersistentUpdate() {
        FakePolicyRepository policies = new FakePolicyRepository(false);
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(
                new FakeAuditRepository(), policies);

        ConfigAuditPolicySnapshot defaults = service.policy("");

        assertEquals("GLOBAL", defaults.policy().projectId());
        assertEquals(180, defaults.policy().retentionDays());
        assertTrue(defaults.policy().maskingEnabled());
        assertFalse(defaults.persistent());
        assertEquals("审计策略持久化能力未初始化",
                assertThrows(IllegalStateException.class,
                        () -> service.updatePolicy(policy("project-1", 90))).getMessage());
    }

    @Test
    void policyReadFailureFallsBackToTypedDefaults() {
        FakePolicyRepository policies = new FakePolicyRepository(true);
        policies.failFind = true;
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(
                new FakeAuditRepository(), policies);

        ConfigAuditPolicySnapshot fallback = service.policy("project-1");

        assertEquals("project-1", fallback.policy().projectId());
        assertEquals(180, fallback.policy().retentionDays());
        assertTrue(fallback.persistent());
    }

    @Test
    void updatesPersistentPolicyThroughRepository() {
        FakePolicyRepository policies = new FakePolicyRepository(true);
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(
                new FakeAuditRepository(), policies);

        ConfigAuditPolicySnapshot saved = service.updatePolicy(policy("project-1", 90));

        assertEquals("project-1", saved.policy().projectId());
        assertEquals(90, saved.policy().retentionDays());
        assertTrue(saved.persistent());
    }

    @Test
    void retentionClampsProjectSearchAndHidesExpiredDetailsAndOperatorRows() {
        FakeAuditRepository audits = new FakeAuditRepository();
        FakePolicyRepository policies = new FakePolicyRepository(true);
        policies.save(policy("project-1", 30));
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(audits, policies);
        ConfigAuditEntry recent = audits.append(draft("recent", LocalDateTime.now().minusDays(5)));
        ConfigAuditEntry expired = audits.append(draft("expired", LocalDateTime.now().minusDays(45)));

        List<ConfigAuditEntry> visible = service.search(new AuditQuery(
                "project-1", "", "", "", "", "", "", "", 100));

        assertEquals(List.of(recent), visible);
        LocalDateTime clamped = LocalDateTime.parse(audits.criteria.startTime().replace(' ', 'T'));
        assertTrue(clamped.isAfter(LocalDateTime.now().minusDays(31)));
        assertTrue(service.detail("recent").isPresent());
        assertTrue(service.detail("expired").isEmpty());
        assertEquals(List.of(recent), service.listForOperator("alice", 100));
        assertFalse(service.listForOperator("alice", 100).contains(expired));
    }

    @Test
    void maskingCannotBeDisabledByStoredAuditPolicy() {
        FakePolicyRepository policies = new FakePolicyRepository(true);
        ConfigAuditApplicationService service = new ConfigAuditApplicationService(
                new FakeAuditRepository(), policies);

        ConfigAuditPolicySnapshot saved = service.updatePolicy(new AuditPolicy(
                "project-1", 90, false, false, false, false, AuditPolicyStatus.DISABLED));

        assertTrue(saved.policy().maskingEnabled());
        assertFalse(saved.policy().exportApprovalRequired());
        assertFalse(saved.policy().highRiskConfirmationRequired());
        assertFalse(saved.policy().replayEnabled());
    }

    private AuditPolicy policy(String projectId, int retentionDays) {
        return new AuditPolicy(projectId, retentionDays, true, true, true, true, AuditPolicyStatus.ENABLED);
    }

    private ConfigAuditDraft draft(String auditId) {
        return draft(auditId, LocalDateTime.now());
    }

    private ConfigAuditDraft draft(String auditId, LocalDateTime createTime) {
        return new ConfigAuditDraft(
                auditId, "project-1", "agent-1", "skill", "update", "skill", "skill-1",
                "MEDIUM", "SUCCESS", "u1", "alice", "admin", "127.0.0.1", "trace-1",
                "{}", "{}", createTime);
    }

    private static final class FakeAuditRepository implements IConfigAuditRepository {
        private final List<ConfigAuditEntry> entries = new ArrayList<>();
        private ConfigAuditCriteria criteria;
        private int operatorLimit;

        @Override
        public ConfigAuditEntry append(ConfigAuditDraft draft) {
            Optional<ConfigAuditEntry> existing = find(draft.auditId());
            if (existing.isPresent()) return existing.get();
            ConfigAuditEntry entry = ConfigAuditEntry.from(draft);
            entries.add(entry);
            return entry;
        }

        @Override
        public List<ConfigAuditEntry> search(ConfigAuditCriteria criteria) {
            this.criteria = criteria;
            return List.copyOf(entries);
        }

        @Override
        public Optional<ConfigAuditEntry> find(String auditId) {
            return entries.stream().filter(entry -> entry.auditId().equals(auditId)).findFirst();
        }

        @Override
        public List<ConfigAuditEntry> listForOperator(String operator, int limit) {
            this.operatorLimit = limit;
            return entries.stream()
                    .filter(entry -> entry.operatorId().equals(operator) || entry.operatorName().equals(operator))
                    .limit(limit)
                    .toList();
        }

        @Override
        public ConfigAuditReadiness readiness() {
            return new ConfigAuditReadiness("AuditStore", "UP", true, false, "");
        }
    }

    private static final class FakePolicyRepository implements IConfigAuditPolicyRepository {
        private final boolean persistent;
        private ConfigAuditPolicySnapshot snapshot;
        private boolean failFind;

        private FakePolicyRepository(boolean persistent) {
            this.persistent = persistent;
        }

        @Override
        public Optional<ConfigAuditPolicySnapshot> find(String projectId) {
            if (failFind) throw new IllegalStateException("policy read failed");
            return Optional.ofNullable(snapshot)
                    .filter(value -> value.policy().projectId().equals(projectId));
        }

        @Override
        public ConfigAuditPolicySnapshot save(AuditPolicy policy) {
            snapshot = new ConfigAuditPolicySnapshot(policy, persistent, "now");
            return snapshot;
        }

        @Override
        public boolean persistent() {
            return persistent;
        }
    }
}
