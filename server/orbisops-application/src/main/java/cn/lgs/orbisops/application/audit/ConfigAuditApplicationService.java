package cn.lgs.orbisops.application.audit;

import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditPolicyRepository;
import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditRepository;
import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.AuditPolicyStatus;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditDraft;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditEntry;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditReadiness;
import cn.lgs.orbisops.domain.audit.service.ConfigAuditRiskPolicy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ConfigAuditApplicationService {

    private static final DateTimeFormatter MYSQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IConfigAuditRepository audits;
    private final IConfigAuditPolicyRepository policies;
    private final ConfigAuditRiskPolicy riskPolicy;

    public ConfigAuditApplicationService(
            IConfigAuditRepository audits,
            IConfigAuditPolicyRepository policies) {
        this(audits, policies, new ConfigAuditRiskPolicy());
    }

    ConfigAuditApplicationService(
            IConfigAuditRepository audits,
            IConfigAuditPolicyRepository policies,
            ConfigAuditRiskPolicy riskPolicy) {
        if (audits == null) throw new IllegalArgumentException("CONFIG_AUDIT_REPOSITORY_REQUIRED");
        if (policies == null) throw new IllegalArgumentException("CONFIG_AUDIT_POLICY_REPOSITORY_REQUIRED");
        if (riskPolicy == null) throw new IllegalArgumentException("CONFIG_AUDIT_RISK_POLICY_REQUIRED");
        this.audits = audits;
        this.policies = policies;
        this.riskPolicy = riskPolicy;
    }

    public ConfigAuditEntry record(ConfigAuditCommand command) {
        return recordIdempotent(command, "");
    }

    public ConfigAuditEntry recordIdempotent(ConfigAuditCommand command, String deliveryKey) {
        if (command == null) throw new IllegalArgumentException("CONFIG_AUDIT_COMMAND_REQUIRED");
        if (command.moduleName().isBlank() || command.actionName().isBlank()) {
            throw new IllegalArgumentException("CONFIG_AUDIT_MODULE_ACTION_REQUIRED");
        }
        ConfigAuditDraft draft = new ConfigAuditDraft(
                auditId(deliveryKey),
                command.projectId(),
                command.agentId(),
                command.moduleName(),
                command.actionName(),
                command.targetType().isBlank() ? command.moduleName() : command.targetType(),
                command.targetId(),
                riskPolicy.resolve(command.moduleName(), command.actionName(), command.riskLevel()),
                command.resultStatus().isBlank() ? "SUCCESS" : command.resultStatus(),
                command.operatorId(),
                command.operatorName(),
                command.operatorRole(),
                command.clientIp(),
                command.traceId(),
                command.beforeJson(),
                command.afterJson(),
                LocalDateTime.now());
        return audits.append(draft);
    }

    public List<ConfigAuditEntry> search(AuditQuery query) {
        if (query == null) throw new IllegalArgumentException("AUDIT_QUERY_REQUIRED");
        int requestedLimit = Math.min(query.limit(), 500);
        Map<String, LocalDateTime> cutoffs = new HashMap<>();
        if (!query.projectId().isBlank()) {
            AuditQuery effective = clampRetention(query.withLimit(requestedLimit), query.projectId(), cutoffs);
            return audits.search(effective.toCriteria()).stream()
                    .filter(entry -> visibleWithinRetention(entry, cutoffs))
                    .limit(requestedLimit)
                    .toList();
        }
        return audits.search(query.withLimit(500).toCriteria()).stream()
                .filter(entry -> visibleWithinRetention(entry, cutoffs))
                .limit(requestedLimit)
                .toList();
    }

    public Optional<ConfigAuditEntry> detail(String auditId) {
        String normalized = required(auditId, "AUDIT_ID_REQUIRED");
        Map<String, LocalDateTime> cutoffs = new HashMap<>();
        return audits.find(normalized).filter(entry -> visibleWithinRetention(entry, cutoffs));
    }

    public List<ConfigAuditEntry> listForOperator(String operator, int limit) {
        String normalized = required(operator, "AUDIT_OPERATOR_REQUIRED");
        int requestedLimit = Math.max(1, Math.min(limit, 200));
        Map<String, LocalDateTime> cutoffs = new HashMap<>();
        return audits.listForOperator(normalized, 200).stream()
                .filter(entry -> visibleWithinRetention(entry, cutoffs))
                .limit(requestedLimit)
                .toList();
    }

    public ConfigAuditPolicySnapshot policy(String projectId) {
        String normalized = policyProjectId(projectId);
        try {
            return policies.find(normalized).orElseGet(() -> defaultPolicy(normalized));
        } catch (RuntimeException ignored) {
            return defaultPolicy(normalized);
        }
    }

    public ConfigAuditPolicySnapshot updatePolicy(AuditPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("AUDIT_POLICY_REQUIRED");
        if (!policies.persistent()) {
            throw new IllegalStateException("审计策略持久化能力未初始化");
        }
        AuditPolicy safePolicy = new AuditPolicy(
                policy.projectId(),
                policy.retentionDays(),
                true,
                policy.exportApprovalRequired(),
                policy.highRiskConfirmationRequired(),
                policy.replayEnabled(),
                policy.status());
        return policies.save(safePolicy);
    }

    public ConfigAuditReadiness readiness() {
        return audits.readiness();
    }

    private String auditId(String deliveryKey) {
        String normalized = value(deliveryKey);
        if (normalized.isBlank()) return UUID.randomUUID().toString();
        return UUID.nameUUIDFromBytes(
                ("config-audit:" + normalized).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private ConfigAuditPolicySnapshot defaultPolicy(String projectId) {
        return new ConfigAuditPolicySnapshot(
                new AuditPolicy(projectId, 180, true, true, true, true, AuditPolicyStatus.ENABLED),
                policies.persistent(),
                "");
    }

    private AuditQuery clampRetention(
            AuditQuery query,
            String projectId,
            Map<String, LocalDateTime> cutoffs) {
        LocalDateTime cutoff = retentionCutoff(projectId, cutoffs);
        String requested = value(query.startTime());
        if (requested.isBlank()) return query.withStartTime(MYSQL_DATE_TIME.format(cutoff));
        LocalDateTime parsed = parseDateTime(requested);
        if (parsed == null || parsed.isBefore(cutoff)) {
            return query.withStartTime(MYSQL_DATE_TIME.format(cutoff));
        }
        return query;
    }

    private boolean visibleWithinRetention(
            ConfigAuditEntry entry,
            Map<String, LocalDateTime> cutoffs) {
        if (entry == null || entry.createTime() == null) return false;
        return !entry.createTime().isBefore(retentionCutoff(entry.projectId(), cutoffs));
    }

    private LocalDateTime retentionCutoff(String projectId, Map<String, LocalDateTime> cutoffs) {
        String normalized = policyProjectId(projectId);
        return cutoffs.computeIfAbsent(normalized, key -> {
            int retentionDays = policy(key).policy().retentionDays();
            return LocalDateTime.now().minusDays(retentionDays);
        });
    }

    private LocalDateTime parseDateTime(String input) {
        String normalized = value(input);
        if (normalized.isBlank()) return null;
        try {
            return LocalDateTime.parse(normalized, MYSQL_DATE_TIME);
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(normalized);
            } catch (RuntimeException invalid) {
                return null;
            }
        }
    }

    private String policyProjectId(String projectId) {
        String normalized = value(projectId);
        return normalized.isBlank() ? "GLOBAL" : normalized;
    }

    private String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
