package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcWorkflowApprovalRepository implements WorkflowApprovalRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkflowApprovalRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public void ensureSchema() {
        requiredTemplate().execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_workflow_approval (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  approval_id VARCHAR(96) NOT NULL,
                  run_id VARCHAR(100) NOT NULL,
                  project_id VARCHAR(128) NOT NULL,
                  node_id VARCHAR(128) NOT NULL,
                  wait_token_hash CHAR(64) NOT NULL,
                  approve_action_hash CHAR(64) NOT NULL,
                  reject_action_hash CHAR(64) NOT NULL,
                  status VARCHAR(24) NOT NULL DEFAULT 'WAITING',
                  channel_id VARCHAR(80) NOT NULL DEFAULT '',
                  target VARCHAR(256) NOT NULL DEFAULT '',
                  request_summary TEXT NOT NULL,
                  requested_at DATETIME(3) NOT NULL,
                  expires_at DATETIME(3) NOT NULL,
                  decided_by VARCHAR(128) NOT NULL DEFAULT '',
                  decided_at DATETIME(3) NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_workflow_approval_id (approval_id),
                  UNIQUE KEY uk_workflow_approval_run_node (run_id, node_id),
                  UNIQUE KEY uk_workflow_approval_approve_action (approve_action_hash),
                  UNIQUE KEY uk_workflow_approval_reject_action (reject_action_hash),
                  KEY idx_workflow_approval_project_status (project_id, status, requested_at),
                  KEY idx_workflow_approval_expiry (status, expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable HUMAN_APPROVAL workflow decision ledger'
                """);
    }

    @Override
    public Optional<WorkflowApprovalRecord> findByRunNode(String runId, String nodeId) {
        return requiredTemplate().query("""
                SELECT * FROM ai_ops_workflow_approval WHERE run_id=? AND node_id=? LIMIT 1
                """, this::record, runId, nodeId).stream().findFirst();
    }

    @Override
    public Optional<WorkflowApprovalRecord> findCurrentByRun(String runId) {
        return requiredTemplate().query("""
                SELECT * FROM ai_ops_workflow_approval
                WHERE run_id=? ORDER BY requested_at DESC, id DESC LIMIT 1
                """, this::record, runId).stream().findFirst();
    }

    @Override
    public Optional<WorkflowApprovalRecord> findByActionHash(String actionHash) {
        return requiredTemplate().query("""
                SELECT * FROM ai_ops_workflow_approval
                WHERE approve_action_hash=? OR reject_action_hash=? LIMIT 1
                """, this::record, actionHash, actionHash).stream().findFirst();
    }

    @Override
    public boolean insert(WorkflowApprovalRecord record) {
        if (record == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_RECORD_REQUIRED");
        try {
            return requiredTemplate().update("""
                    INSERT INTO ai_ops_workflow_approval
                      (approval_id,run_id,project_id,node_id,wait_token_hash,approve_action_hash,reject_action_hash,
                       status,channel_id,target,request_summary,requested_at,expires_at,decided_by,decided_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    record.approvalId(), record.runId(), record.projectId(), record.nodeId(), record.waitTokenHash(),
                    record.approveActionHash(), record.rejectActionHash(), record.status().name(), record.channelId(),
                    record.target(), record.requestSummary(), timestamp(record.requestedAt()), timestamp(record.expiresAt()),
                    record.decidedBy(), timestamp(record.decidedAt())) == 1;
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public boolean rotateActionsIfWaiting(String approvalId,
                                          String approveActionHash,
                                          String rejectActionHash,
                                          Instant expiresAt) {
        return requiredTemplate().update("""
                UPDATE ai_ops_workflow_approval
                SET approve_action_hash=?, reject_action_hash=?, expires_at=?
                WHERE approval_id=? AND status='WAITING'
                """, approveActionHash, rejectActionHash, timestamp(expiresAt), approvalId) == 1;
    }

    @Override
    public boolean decideIfWaiting(String approvalId,
                                   WorkflowApprovalRecord.Decision decision,
                                   String actor,
                                   Instant decidedAt) {
        if (decision == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_DECISION_REQUIRED");
        String next = decision == WorkflowApprovalRecord.Decision.APPROVE ? "APPROVED" : "REJECTED";
        return requiredTemplate().update("""
                UPDATE ai_ops_workflow_approval
                SET status=?, decided_by=?, decided_at=?
                WHERE approval_id=? AND status='WAITING' AND expires_at>?
                """, next, text(actor), timestamp(decidedAt), approvalId, timestamp(decidedAt)) == 1;
    }

    @Override
    public boolean expireIfWaiting(String approvalId, Instant expiredAt) {
        return requiredTemplate().update("""
                UPDATE ai_ops_workflow_approval
                SET status='EXPIRED', decided_at=?
                WHERE approval_id=? AND status='WAITING'
                """, timestamp(expiredAt), approvalId) == 1;
    }

    private WorkflowApprovalRecord record(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowApprovalRecord(
                rs.getString("approval_id"),
                rs.getString("run_id"),
                rs.getString("project_id"),
                rs.getString("node_id"),
                rs.getString("wait_token_hash"),
                rs.getString("approve_action_hash"),
                rs.getString("reject_action_hash"),
                WorkflowApprovalRecord.Status.valueOf(rs.getString("status")),
                rs.getString("channel_id"),
                rs.getString("target"),
                rs.getString("request_summary"),
                instant(rs, "requested_at"),
                instant(rs, "expires_at"),
                rs.getString("decided_by"),
                instant(rs, "decided_at"));
    }

    private JdbcTemplate requiredTemplate() {
        if (jdbcTemplate == null) throw new IllegalStateException("WORKFLOW_APPROVAL_STORE_UNAVAILABLE");
        return jdbcTemplate;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
