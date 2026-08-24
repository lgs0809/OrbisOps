package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangeVerificationQueuePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.PessimisticLockingFailureException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcChangeVerificationQueue implements ChangeVerificationQueuePort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate discoveryTransaction;
    public JdbcChangeVerificationQueue(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("mysqlTransactionManager") PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        discoveryTransaction = new TransactionTemplate(transactions);
        // INSERT SELECT must not acquire shared next-key locks on the queue's empty range.
        discoveryTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        discoveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public int discover(Binding b, int limit) {
        for (int attempt = 0; ; attempt++) {
            try { return discoveryTransaction.execute(status -> discoverCommitted(b, limit)); }
            catch (PessimisticLockingFailureException contention) {
                // Retry the whole rolled-back transaction; the event key preserves exactly-once admission.
                if (attempt >= 2) throw contention;
            }
        }
    }

    private int discoverCommitted(Binding b, int limit) {
        return jdbc.update("""
                INSERT INTO ai_ops_change_verification_job
                (event_key,project_id,package_id,approved_version,approved_hash,landing_run_id,owner,
                 workflow_id,workflow_version,workflow_hash,run_id,session_id,next_attempt_at)
                SELECT x.event_key,x.project_id,x.package_id,x.approved_version,x.approved_package_hash,
                       x.landing_run_id,x.create_by,?,?,?,CONCAT('auto-c-',x.event_key),
                       CONCAT('auto-c-session-',x.event_key),TIMESTAMPADD(SECOND,900,x.finished_at)
                FROM (
                  SELECT c.*,l.finished_at,SHA2(CAST(JSON_ARRAY(c.project_id,c.package_id,c.approved_version,
                       c.approved_package_hash,c.landing_run_id) AS CHAR),256) AS event_key
                  FROM ai_ops_change_package c JOIN ai_ops_change_package_landing_run l
                    ON l.run_id=c.landing_run_id AND l.package_id=c.package_id AND l.project_id=c.project_id
                    AND l.approved_version=c.approved_version AND l.approved_package_hash=c.approved_package_hash
                  WHERE c.project_id=? AND c.status='LANDED' AND l.status='SUCCEEDED'
                    AND c.approved_version>0 AND c.approved_package_hash<>'' AND c.create_by<>''
                    AND l.finished_at IS NOT NULL
                ) x LEFT JOIN ai_ops_change_verification_job j ON j.event_key=x.event_key
                WHERE j.event_key IS NULL ORDER BY x.finished_at,x.package_id LIMIT ?
                ON DUPLICATE KEY UPDATE event_key=VALUES(event_key)
                """, b.workflowId(), b.version(), b.definitionHash(), b.projectId(), Math.max(1, Math.min(50, limit)));
    }

    @Override
    @Transactional(transactionManager="mysqlTransactionManager", isolation=Isolation.READ_COMMITTED)
    public Optional<Task> claim() {
        var rows = jdbc.queryForList("""
                SELECT event_key FROM ai_ops_change_verification_job
                WHERE (status IN ('PENDING','BLOCKED') AND next_attempt_at<=CURRENT_TIMESTAMP(3))
                   OR (status='LEASED' AND lease_until<=CURRENT_TIMESTAMP(3))
                ORDER BY next_attempt_at,event_key LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
        if (rows.isEmpty()) return Optional.empty();
        String key = String.valueOf(rows.get(0).get("event_key"));
        String token = UUID.randomUUID().toString();
        int claimed = jdbc.update("""
                UPDATE ai_ops_change_verification_job SET status='LEASED',lease_token=?,
                  lease_until=TIMESTAMPADD(SECOND,300,CURRENT_TIMESTAMP(3))
                WHERE event_key=? AND ((status IN ('PENDING','BLOCKED') AND next_attempt_at<=CURRENT_TIMESTAMP(3))
                   OR (status='LEASED' AND lease_until<=CURRENT_TIMESTAMP(3)))
                """, token, key);
        if (claimed != 1) return Optional.empty();
        return jdbc.query("SELECT * FROM ai_ops_change_verification_job WHERE event_key=?", (r, n) ->
                new Task(r.getString("event_key"),r.getString("project_id"),r.getString("package_id"),
                        r.getInt("approved_version"),r.getString("approved_hash"),r.getString("landing_run_id"),
                        r.getString("owner"),r.getString("workflow_id"),r.getInt("workflow_version"),
                        r.getString("workflow_hash"),r.getString("run_id"),r.getString("session_id"),
                        r.getString("lease_token"),r.getInt("failures")), key).stream().findFirst();
    }

    @Override public boolean owns(Task task) {
        return Integer.valueOf(1).equals(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_ops_change_verification_job WHERE event_key=? AND lease_token=?
                  AND status='LEASED' AND lease_until>CURRENT_TIMESTAMP(3)
                """,Integer.class,task.eventKey(),task.leaseToken()));
    }

    @Override public boolean settle(Task t, String status, String reason, int delaySeconds, boolean failedAttempt) {
        if (!java.util.Set.of("PENDING","COMPLETED","FAILED","CANCELED","BLOCKED").contains(status))
            throw new IllegalArgumentException("VERIFICATION_JOB_STATUS_INVALID");
        String safe = reason == null ? "" : reason.substring(0, Math.min(300, reason.length()));
        return jdbc.update("""
                UPDATE ai_ops_change_verification_job SET status=?,last_error=?,failures=failures+?,
                  next_attempt_at=TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP(3)),lease_token='',lease_until=NULL
                WHERE event_key=? AND lease_token=? AND status='LEASED' AND lease_until>CURRENT_TIMESTAMP(3)
                """,status,safe,failedAttempt?1:0,Math.max(0,delaySeconds),t.eventKey(),t.leaseToken())==1;
    }
}
