package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageApprovalRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApproval;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChangePackageApprovalRepository implements IChangePackageApprovalRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public JdbcChangePackageApprovalRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public JdbcChangePackageApprovalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public void saveDecision(ChangePackageApproval approval) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_change_package_approval_record
                (approval_id, package_id, project_id, version, package_hash, risk_level, approver,
                 actor_scope, decision, admin_confirmation, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  risk_level=VALUES(risk_level),
                  actor_scope=VALUES(actor_scope),
                  decision=VALUES(decision),
                  admin_confirmation=VALUES(admin_confirmation),
                  metadata_json=VALUES(metadata_json)
                """, approval.approvalId(), approval.packageId(), approval.projectId(), approval.version(),
                approval.packageHash(), approval.riskLevel(), approval.approver(), approval.actorScope(),
                approval.decision(), approval.adminConfirmation() ? 1 : 0,
                ChangePackageJsonMapCodec.encode(approval.metadata()));
    }

    @Override
    public int countDistinctApproved(String packageId, int version, String packageHash) {
        requireAvailable();
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT approver)
                FROM ai_ops_change_package_approval_record
                WHERE package_id=? AND version=? AND package_hash=? AND decision='APPROVED'
                """, Integer.class, packageId, version, packageHash);
        return count == null ? 0 : count;
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_STORE_UNAVAILABLE");
    }
}
