package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionJobRepository;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRunCandidate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** JDBC persistence owner for Skill Evolution jobs and generated patches. */
@Repository
public class JdbcSkillEvolutionJobRepository implements ISkillEvolutionJobRepository {

    private static final String JOB_SELECT = """
            SELECT j.id,j.job_id,j.run_id,j.session_id,j.project_id,j.agent_id,j.trigger_reason,j.status,
                   j.attempts,j.next_run_at,j.last_error,j.create_time,j.update_time,
                   COALESCE(s.source_id,'') AS source_id,COALESCE(s.lease_token,'') AS lease_token,
                   COALESCE(s.epoch,0) AS epoch,COALESCE(s.lease_until_ms,0) AS lease_until_ms,
                   COALESCE(s.ordinary_failures,j.attempts) AS ordinary_failures
            FROM ai_ops_skill_evolution_job j LEFT JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
            """;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcSkillEvolutionJobRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", isolation = Isolation.READ_COMMITTED)
    public SkillEvolutionJobSnapshot enqueue(SkillEvolutionJobSnapshot job) {
        if (job == null) throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_REQUIRED");
        JdbcTemplate template = requiredTemplate();
        var runs=template.queryForList("SELECT run_id,session_id,project_id,agent_id FROM ai_ops_agent_run WHERE run_id=?",job.runId());
        if (runs.size()!=1 || !job.sessionId().equals(runs.get(0).get("session_id"))
                || !job.projectId().equals(runs.get(0).get("project_id")) || !job.agentId().equals(runs.get(0).get("agent_id")))
            throw new IllegalArgumentException("SKILL_EVOLUTION_CANONICAL_SCOPE_MISMATCH");
        lockSource(template,job);
        String sourceId=new JdbcSkillEvolutionSourceReader(template).freeze(job);
        var previous=findJob(template,job.jobId());
        boolean explicitUpgrade=!sourceId.isBlank() && "USER_EXPLICIT_REMEMBER".equals(job.triggerReason())
                && previous.filter(p->p.status()==SkillEvolutionJobStatus.SKIPPED
                    && !"USER_EXPLICIT_REMEMBER".equals(p.triggerReason())).isPresent()
                && template.queryForList("""
                    SELECT decision FROM ai_ops_skill_evolution_patch WHERE job_id=?
                    ORDER BY id DESC LIMIT 1
                    """,job.jobId()).stream().anyMatch(p->reconsiderable(value((String)p.get("decision"))));
        boolean groupingUpgrade=!sourceId.isBlank() && "EXPERIENCE_GROUPING_BACKFILL".equals(job.triggerReason())
                && previous.filter(p->p.status()==SkillEvolutionJobStatus.SKIPPED
                    || p.status()==SkillEvolutionJobStatus.FAILED && "Incorrect result size: expected 1, actual 0".equals(p.lastError())).isPresent()
                && !template.queryForList("SELECT source_id FROM ai_ops_skill_evolution_source WHERE source_id=? AND candidate_id=''",sourceId).isEmpty()
                && template.queryForList("SELECT source_id FROM ai_ops_skill_method_experience WHERE source_id=?",sourceId).isEmpty()
                && template.queryForList("SELECT decision FROM ai_ops_skill_evolution_patch WHERE job_id=? ORDER BY id DESC LIMIT 1",job.jobId())
                    .stream().anyMatch(p->java.util.Set.of("SKIP_INSUFFICIENT_REPEATED_OBSERVATIONS","SKIP_INSUFFICIENT_SOURCE_DIVERSITY","SKIP_NO_REUSABLE_PATTERN").contains(p.get("decision")));
        template.update("""
                INSERT INTO ai_ops_skill_evolution_job(job_id,run_id,session_id,project_id,agent_id,trigger_reason,status,attempts,next_run_at)
                VALUES (?,?,?,?,?,?,'PENDING',0,CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE trigger_reason=CASE
                  WHEN VALUES(trigger_reason)='USER_EXPLICIT_REMEMBER' THEN VALUES(trigger_reason)
                  WHEN trigger_reason='USER_EXPLICIT_REMEMBER' THEN trigger_reason ELSE VALUES(trigger_reason) END
                """,job.jobId(),job.runId(),job.sessionId(),job.projectId(),job.agentId(),job.triggerReason());
        template.update("INSERT IGNORE INTO ai_ops_skill_evolution_job_state(job_id) VALUES (?)",job.jobId());
        if (!lockJobAndState(template, job.jobId(), false)) {
            throw new IllegalStateException("SKILL_EVOLUTION_ENQUEUED_JOB_MISSING");
        }
        var stored=findJob(template,job.jobId()).orElseThrow();
        if (!stored.runId().equals(job.runId()) || !stored.sessionId().equals(job.sessionId())
                || !stored.projectId().equals(job.projectId()) || !stored.agentId().equals(job.agentId()))
            throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_IDENTITY_CONFLICT");
        // Explicit operator retry retains the same accepted source and all previous failure audit.
        // A published/proposed candidate is handled by its own lifecycle, never regenerated here.
        boolean manualRetry = "MANUAL_RETRY".equals(job.triggerReason())
                && stored.status() == SkillEvolutionJobStatus.FAILED
                && sourceId.equals(stored.sourceId())
                && !template.queryForList("SELECT source_id FROM ai_ops_skill_evolution_source WHERE source_id=? AND candidate_id=''", sourceId).isEmpty();
        if (!sourceId.isBlank() && (!sourceId.equals(stored.sourceId()) || explicitUpgrade || groupingUpgrade || manualRetry)
                && stored.status()!=SkillEvolutionJobStatus.RUNNING) {
            template.update("UPDATE ai_ops_skill_evolution_job SET status='PENDING',attempts=0,last_error=NULL,next_run_at=CURRENT_TIMESTAMP WHERE job_id=?",job.jobId());
            template.update("UPDATE ai_ops_skill_evolution_job_state SET source_id=?,lease_token='',lease_until_ms=0,epoch=epoch+1,ordinary_failures=0 WHERE job_id=?",sourceId,job.jobId());
        }
        return findJob(template, job.jobId())
                .orElseThrow(() -> new IllegalStateException("SKILL_EVOLUTION_ENQUEUED_JOB_MISSING"));
    }

    @Override
    public List<SkillEvolutionRunCandidate> findUnqueuedRunCandidates(int limit) {
        JdbcTemplate template = requiredTemplate();
        int bounded = Math.max(1, Math.min(limit, 500));
        return template.query("""
                SELECT r.run_id, r.session_id, r.project_id, r.agent_id, r.status, r.updated_at
                FROM ai_ops_task_episode e JOIN ai_ops_task_acceptance a ON a.acceptance_id=e.verified_outcome_ref
                  AND a.project_id=e.project_id AND a.episode_id=e.episode_id AND a.episode_revision=e.revision
                JOIN ai_ops_agent_run r ON r.run_id=a.source_run_id AND r.project_id=a.project_id AND r.session_id=e.session_id
                LEFT JOIN ai_ops_skill_evolution_job j ON j.run_id=r.run_id
                LEFT JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                WHERE e.outcome='SUCCEEDED' AND a.outcome='SUCCEEDED' AND r.status IN ('SUCCEEDED','FAILED','CANCELED')
                  AND (j.job_id IS NULL OR (j.status<>'RUNNING' AND COALESCE(s.source_id,'')<>a.acceptance_id))
                  AND r.project_id<>'' AND r.agent_id<>''
                  AND NOT EXISTS (SELECT 1 FROM ai_ops_chat_message m LEFT JOIN ai_ops_task_episode_turn t
                    ON t.project_id=m.project_id AND t.session_id=m.session_id AND t.source_run_ref=m.turn_id
                    WHERE m.project_id=e.project_id AND m.session_id=e.session_id AND m.role='user'
                      AND (t.id IS NULL OR t.status<>'ASSIGNED'))
                ORDER BY a.created_at ASC,r.run_id ASC LIMIT ?
                """,
                (resultSet, rowNum) -> new SkillEvolutionRunCandidate(
                        resultSet.getString("run_id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("project_id"),
                        resultSet.getString("agent_id"),
                        resultSet.getString("status"),
                        instant(resultSet.getTimestamp("updated_at"))),
                bounded);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", isolation = Isolation.READ_COMMITTED)
    public Optional<SkillEvolutionJobSnapshot> claimPending(int maxAttempts) {
        JdbcTemplate template = requiredTemplate();
        var named = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(template);
        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("maxAttempts", Math.max(1, maxAttempts))
                .addValue("deferred", cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy.DURABLE_FAILURE_CODES);
        // Recover only rows already locked by this worker. A global JOIN UPDATE
        // also acquired locks while other workers were claiming live jobs and
        // could deadlock even when there were no expired leases. SKIP LOCKED
        // uses the same job/state lock acquisition as the pending selection.
        // Recovery and failure accounting still commit atomically, once per lease.
        List<String> expiredIds = template.query("""
                SELECT j.job_id FROM ai_ops_skill_evolution_job j
                JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                WHERE j.status='RUNNING' AND s.lease_until_ms<=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000
                ORDER BY j.id ASC LIMIT 100
                """, (row,index)->row.getString("job_id"));
        for (String expiredId : expiredIds) {
            if (!lockJobAndState(template, expiredId, true)) continue;
            var expired = template.query(JOB_SELECT+"""
                    WHERE j.job_id=? AND j.status='RUNNING'
                      AND s.lease_until_ms<=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000
                    """, this::job, expiredId);
            if (expired.isEmpty()) continue;
            SkillEvolutionJobSnapshot lease = expired.get(0);
            boolean deferred = cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy.DURABLE_FAILURE_CODES
                    .contains(value(lease.lastError()));
            int failures = lease.ordinaryFailures() + (deferred ? 0 : 1);
            template.update("""
                    UPDATE ai_ops_skill_evolution_job SET status=?,last_error=?,next_run_at=CURRENT_TIMESTAMP
                    WHERE job_id=?
                    """, !deferred && failures >= Math.max(1,maxAttempts) ? "FAILED" : "PENDING",
                    deferred ? lease.lastError() : "SKILL_EVOLUTION_LEASE_EXPIRED", lease.jobId());
            template.update("""
                    UPDATE ai_ops_skill_evolution_job_state
                    SET ordinary_failures=?,lease_token='',lease_until_ms=0 WHERE job_id=?
                    """, failures, lease.jobId());
        }
        // Sorting a joined locking query can lock every matching row before
        // LIMIT, starving parallel workers. Read the ordered candidate IDs
        // without locks, then acquire/recheck one exact job/state pair at a time.
        // The second read is authoritative under READ_COMMITTED; stale IDs and
        // busy workers cannot claim or charge the same job twice.
        List<String> pendingIds = named.query("""
                SELECT j.job_id FROM ai_ops_skill_evolution_job j
                JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                WHERE j.status = 'PENDING' AND (j.next_run_at IS NULL OR j.next_run_at <= CURRENT_TIMESTAMP)
                  AND (s.ordinary_failures < :maxAttempts OR j.last_error IN (:deferred))
                ORDER BY COALESCE(j.next_run_at,j.create_time) ASC,j.id ASC LIMIT 100
                """, params, (row,index)->row.getString("job_id"));
        List<SkillEvolutionJobSnapshot> selected = List.of();
        for (String pendingId : pendingIds) {
            if (!lockJobAndState(template, pendingId, true)) continue;
            params.addValue("jobId",pendingId);
            selected = named.query(JOB_SELECT+"""
                    WHERE j.job_id=:jobId AND j.status='PENDING'
                      AND (j.next_run_at IS NULL OR j.next_run_at<=CURRENT_TIMESTAMP)
                      AND (s.ordinary_failures<:maxAttempts OR j.last_error IN (:deferred))
                    """, params, this::job);
            if (!selected.isEmpty()) break;
        }
        if (selected.isEmpty()) return Optional.empty();
        SkillEvolutionJobSnapshot candidate = selected.get(0);
        params.addValue("jobId", candidate.jobId()).addValue("attempts", candidate.attempts());
        int updated = named.update("""
                UPDATE ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                SET j.status = 'RUNNING', j.attempts = j.attempts + 1, j.update_time = CURRENT_TIMESTAMP
                WHERE j.job_id = :jobId AND j.status = 'PENDING' AND j.attempts = :attempts
                  AND (j.next_run_at IS NULL OR j.next_run_at <= CURRENT_TIMESTAMP)
                  AND (s.ordinary_failures < :maxAttempts OR j.last_error IN (:deferred))
                """, params);
        if (updated!=1) return Optional.empty();
        String token=UUID.randomUUID().toString();
        template.update("UPDATE ai_ops_skill_evolution_job_state SET lease_token=?,epoch=epoch+1,lease_until_ms=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000+300000 WHERE job_id=?",token,candidate.jobId());
        var current=findJob(template,candidate.jobId()).orElseThrow();
        return Optional.of(new SkillEvolutionJobSnapshot(candidate.databaseId(),candidate.jobId(),candidate.runId(),candidate.sessionId(),
                candidate.projectId(),candidate.agentId(),candidate.triggerReason(),candidate.status(),candidate.attempts(),candidate.nextRunAt(),
                candidate.lastError(),candidate.createdAt(),candidate.updatedAt(),current.sourceId(),token,current.epoch(),current.leaseUntilMillis(),current.ordinaryFailures()));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", isolation = Isolation.READ_COMMITTED)
    public boolean renewLease(SkillEvolutionJobSnapshot claim) {
        var template = requiredTemplate();
        if (!lockJobAndState(template, claim.jobId(), false)) return false;
        return template.update("""
                UPDATE ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                SET s.lease_until_ms=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000+300000
                WHERE j.job_id=? AND j.run_id=? AND j.project_id=? AND j.session_id=? AND j.agent_id=?
                  AND j.status='RUNNING' AND j.attempts=? AND s.source_id=? AND s.epoch=? AND s.lease_token=?
                  AND s.lease_token<>'' AND s.lease_until_ms>UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000
                """,claim.jobId(),claim.runId(),claim.projectId(),claim.sessionId(),claim.agentId(),claim.attempts()+1,
                claim.sourceId(),claim.epoch(),claim.leaseToken()) == 1;
    }

    @Override
    public List<SkillEvolutionJobSnapshot> findJobs(SkillEvolutionJobStatus status, int limit) {
        JdbcTemplate template = requiredTemplate();
        StringBuilder sql = new StringBuilder(JOB_SELECT+" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND j.status = ?");
            args.add(status.name());
        }
        sql.append(" ORDER BY j.id DESC LIMIT ?");
        args.add(limit);
        return template.query(sql.toString(), this::job, args.toArray());
    }

    @Override
    public Optional<SkillEvolutionJobSnapshot> findJob(String jobId) {
        String id = value(jobId);
        if (id.isBlank()) return Optional.empty();
        return findJob(requiredTemplate(), id);
    }

    private SkillEvolutionPatchSnapshot savePatch(SkillEvolutionPatchSnapshot patch) {
        if (patch == null) throw new IllegalArgumentException("SKILL_EVOLUTION_PATCH_REQUIRED");
        JdbcTemplate template = requiredTemplate();
        template.update("""
                INSERT INTO ai_ops_skill_evolution_patch
                  (patch_id, job_id, run_id, project_id, target_skill_id, decision, patch_json,
                   validation_json, status, applied_version, skipped_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                patch.patchId(),
                patch.jobId(),
                patch.runId(),
                patch.projectId(),
                patch.targetSkillId(),
                patch.decision(),
                patch.patchJson(),
                patch.validationJson(),
                patch.status(),
                patch.appliedVersion(),
                patch.skippedReason());
        return findPatch(template, patch.patchId())
                .orElseThrow(() -> new IllegalStateException("SKILL_EVOLUTION_SAVED_PATCH_MISSING"));
    }

    @Override
    public List<SkillEvolutionPatchSnapshot> findPatches(String jobId, String decision, int limit) {
        JdbcTemplate template = requiredTemplate();
        StringBuilder sql = new StringBuilder("""
                SELECT id, patch_id, job_id, run_id, project_id, target_skill_id, decision, patch_json,
                       validation_json, status, applied_version, skipped_reason, create_time, update_time
                FROM ai_ops_skill_evolution_patch
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(jobId)) {
            sql.append(" AND job_id = ?");
            args.add(jobId.trim());
        }
        if (StringUtils.hasText(decision)) {
            sql.append(" AND decision = ?");
            args.add(decision.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(limit);
        return template.query(sql.toString(), this::patch, args.toArray());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", isolation = Isolation.READ_COMMITTED)
    public Optional<SkillEvolutionPatchSnapshot> complete(SkillEvolutionJobSnapshot claim, SkillEvolutionPatchSnapshot patch, SkillEvolutionJobStatus status) {
        if (status==null || status==SkillEvolutionJobStatus.PENDING || status==SkillEvolutionJobStatus.RUNNING)
            throw new IllegalArgumentException("SKILL_EVOLUTION_TERMINAL_STATUS_REQUIRED");
        if (patch==null || !claim.jobId().equals(patch.jobId()) || !claim.runId().equals(patch.runId()) || !claim.projectId().equals(patch.projectId()))
            throw new IllegalArgumentException("SKILL_EVOLUTION_PATCH_SCOPE_MISMATCH");
        var template=requiredTemplate(); lockSource(template,claim);
        if (!lockJobAndState(template, claim.jobId(), false)) return Optional.empty();
        var source=new JdbcSkillEvolutionSourceReader(template);
        try { source.load(claim); } catch (IllegalStateException e) {
            if ("SKILL_EVOLUTION_CLAIM_LOST".equals(e.getMessage())) return Optional.empty();
            throw e;
        }
        var stored=savePatch(patch);
        boolean reconsider=!claim.sourceId().isBlank() && reconsiderable(patch.decision())
                && !"USER_EXPLICIT_REMEMBER".equals(claim.triggerReason())
                && findJob(template,claim.jobId()).filter(j->"USER_EXPLICIT_REMEMBER".equals(j.triggerReason())).isPresent();
        if (reconsider) {
            // An explicit request may arrive while the old background attempt is in flight.
            // Keep its skipped patch as audit, then evaluate the stronger request exactly once.
            template.update("UPDATE ai_ops_skill_evolution_job SET status='PENDING',attempts=0,last_error=NULL,next_run_at=CURRENT_TIMESTAMP WHERE job_id=?",claim.jobId());
            template.update("UPDATE ai_ops_skill_evolution_job_state SET ordinary_failures=0 WHERE job_id=?",claim.jobId());
        } else {
            template.update("UPDATE ai_ops_skill_evolution_job SET status=?,last_error=NULL,update_time=CURRENT_TIMESTAMP WHERE job_id=?",status.name(),claim.jobId());
        }
        template.update("UPDATE ai_ops_skill_evolution_job_state SET lease_token='',lease_until_ms=0 WHERE job_id=?",claim.jobId());
        return Optional.of(stored);
    }

    private boolean reconsiderable(String decision) {
        return java.util.Set.of("SKIP_NO_REUSABLE_PATTERN","SKIP_NO_SIGNAL").contains(decision);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", isolation = Isolation.READ_COMMITTED)
    public boolean rescheduleOrFail(SkillEvolutionJobSnapshot claim, SkillEvolutionRetryTransition transition, String lastError) {
        if (transition == null) throw new IllegalArgumentException("SKILL_EVOLUTION_RETRY_TRANSITION_REQUIRED");
        if (transition.status()==SkillEvolutionJobStatus.RUNNING || transition.status()==SkillEvolutionJobStatus.COMPLETED)
            throw new IllegalArgumentException("SKILL_EVOLUTION_RETRY_STATUS_INVALID");
        boolean waitingForModel=transition.status()==SkillEvolutionJobStatus.PENDING
                && cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy.WAIT_FAILURE_CODES.contains(lastError)
                && transition.attempts()==claim.attempts();
        if(!waitingForModel && transition.attempts()!=claim.attempts()+1)
            throw new IllegalArgumentException("SKILL_EVOLUTION_RETRY_ATTEMPTS_INVALID");
        var template = requiredTemplate();
        if (!lockJobAndState(template, claim.jobId(), false)) return false;
        return template.update("""
                UPDATE ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                SET j.status=?,j.attempts=?,j.last_error=?,j.next_run_at=?,s.lease_token='',s.lease_until_ms=0,
                    s.ordinary_failures=s.ordinary_failures+?
                WHERE j.job_id=? AND j.status='RUNNING' AND j.attempts=? AND s.source_id=? AND s.epoch=? AND s.lease_token=?
                  AND s.lease_token<>'' AND s.lease_until_ms>UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000
                  AND s.ordinary_failures=?
                """,transition.status().name(),transition.attempts(),value(lastError),timestamp(transition.nextRunAt()),
                cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy.ordinaryFailure(lastError,transition.status())?1:0,
                claim.jobId(),claim.attempts()+1,claim.sourceId(),claim.epoch(),claim.leaseToken(),claim.ordinaryFailures())>0;
    }

    private void lockSource(JdbcTemplate template,SkillEvolutionJobSnapshot job) {
        template.queryForList("SELECT session_id FROM ai_ops_task_episode_session WHERE session_id=? AND project_id=? FOR UPDATE",job.sessionId(),job.projectId());
        template.queryForList("SELECT e.episode_id FROM ai_ops_task_episode e JOIN ai_ops_task_episode_turn t ON t.episode_id=e.episode_id AND t.project_id=e.project_id WHERE t.project_id=? AND t.source_run_ref=? FOR UPDATE",job.projectId(),job.runId());
    }

    /** Claim, renewal and terminal transitions all acquire the job before its state row. */
    private boolean lockJobAndState(JdbcTemplate template, String jobId, boolean skipLocked) {
        // A joined SKIP LOCKED read lets the optimizer choose which table is locked
        // first. Contenders can retain different partial locks yet all return no row.
        // Lock one canonical key first; the following join is only an authoritative read.
        String suffix = skipLocked ? " FOR UPDATE SKIP LOCKED" : " FOR UPDATE";
        var jobs = template.queryForList("SELECT job_id FROM ai_ops_skill_evolution_job WHERE job_id=?" + suffix, jobId);
        if (jobs.isEmpty()) return false;
        return !template.queryForList("SELECT job_id FROM ai_ops_skill_evolution_job_state WHERE job_id=?" + suffix, jobId).isEmpty();
    }

    private Optional<SkillEvolutionJobSnapshot> findJob(JdbcTemplate template, String jobId) {
        return template.query(JOB_SELECT+" WHERE j.job_id=?",this::job,jobId).stream().findFirst();
    }

    private Optional<SkillEvolutionPatchSnapshot> findPatch(JdbcTemplate template, String patchId) {
        return template.query("""
                        SELECT id, patch_id, job_id, run_id, project_id, target_skill_id, decision, patch_json,
                               validation_json, status, applied_version, skipped_reason, create_time, update_time
                        FROM ai_ops_skill_evolution_patch
                        WHERE patch_id = ?
                        """,
                this::patch,
                patchId).stream().findFirst();
    }

    private SkillEvolutionJobSnapshot job(ResultSet resultSet, int rowNum) throws SQLException {
        return new SkillEvolutionJobSnapshot(
                resultSet.getLong("id"),
                resultSet.getString("job_id"),
                resultSet.getString("run_id"),
                resultSet.getString("session_id"),
                resultSet.getString("project_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("trigger_reason"),
                SkillEvolutionJobStatus.require(resultSet.getString("status")),
                resultSet.getInt("attempts"),
                instant(resultSet.getTimestamp("next_run_at")),
                resultSet.getString("last_error"),
                instant(resultSet.getTimestamp("create_time")),
                instant(resultSet.getTimestamp("update_time")),
                resultSet.getString("source_id"),resultSet.getString("lease_token"),resultSet.getLong("epoch"),resultSet.getLong("lease_until_ms"),resultSet.getInt("ordinary_failures"));
    }

    private SkillEvolutionPatchSnapshot patch(ResultSet resultSet, int rowNum) throws SQLException {
        return new SkillEvolutionPatchSnapshot(
                resultSet.getLong("id"),
                resultSet.getString("patch_id"),
                resultSet.getString("job_id"),
                resultSet.getString("run_id"),
                resultSet.getString("project_id"),
                resultSet.getString("target_skill_id"),
                resultSet.getString("decision"),
                resultSet.getString("patch_json"),
                resultSet.getString("validation_json"),
                resultSet.getString("status"),
                resultSet.getObject("applied_version", Integer.class),
                resultSet.getString("skipped_reason"),
                instant(resultSet.getTimestamp("create_time")),
                instant(resultSet.getTimestamp("update_time")));
    }

    private JdbcTemplate requiredTemplate() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException("Skill Evolution Job Store 未配置");
        return template;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
