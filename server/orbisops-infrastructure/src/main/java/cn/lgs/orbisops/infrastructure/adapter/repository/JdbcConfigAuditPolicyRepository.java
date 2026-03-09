package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditPolicyRepository;
import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.AuditPolicyStatus;
import cn.lgs.orbisops.domain.audit.model.ConfigAuditPolicySnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcConfigAuditPolicyRepository implements IConfigAuditPolicyRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcConfigAuditPolicyRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public Optional<ConfigAuditPolicySnapshot> find(String projectId) {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) return Optional.empty();
        String normalized = projectId == null || projectId.isBlank() ? "GLOBAL" : projectId.trim();
        List<ConfigAuditPolicySnapshot> rows = jdbc.query("""
                SELECT project_id, retention_days, masking_enabled, export_approval_required,
                       high_risk_confirmation_required, replay_enabled, status,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
                FROM ai_ops_audit_policy
                WHERE project_id=?
                LIMIT 1
                """, (rs, rowNum) -> new ConfigAuditPolicySnapshot(
                new AuditPolicy(
                        rs.getString("project_id"),
                        rs.getInt("retention_days"),
                        rs.getInt("masking_enabled") == 1,
                        rs.getInt("export_approval_required") == 1,
                        rs.getInt("high_risk_confirmation_required") == 1,
                        rs.getInt("replay_enabled") == 1,
                        AuditPolicyStatus.require(rs.getString("status"))),
                true,
                rs.getString("update_time")), normalized);
        return rows.stream().findFirst();
    }

    @Override
    public ConfigAuditPolicySnapshot save(AuditPolicy policy) {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) throw new IllegalStateException("审计策略持久化能力未初始化");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO ai_ops_audit_policy
                  (project_id, retention_days, masking_enabled, export_approval_required,
                   high_risk_confirmation_required, replay_enabled, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  retention_days=VALUES(retention_days),
                  masking_enabled=VALUES(masking_enabled),
                  export_approval_required=VALUES(export_approval_required),
                  high_risk_confirmation_required=VALUES(high_risk_confirmation_required),
                  replay_enabled=VALUES(replay_enabled),
                  status=VALUES(status),
                  update_time=VALUES(update_time)
                """,
                policy.projectId(),
                policy.retentionDays(),
                policy.maskingEnabled() ? 1 : 0,
                policy.exportApprovalRequired() ? 1 : 0,
                policy.highRiskConfirmationRequired() ? 1 : 0,
                policy.replayEnabled() ? 1 : 0,
                policy.status().name(),
                now,
                now);
        return find(policy.projectId()).orElse(new ConfigAuditPolicySnapshot(policy, true, now.toString()));
    }

    @Override
    public boolean persistent() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }
}
