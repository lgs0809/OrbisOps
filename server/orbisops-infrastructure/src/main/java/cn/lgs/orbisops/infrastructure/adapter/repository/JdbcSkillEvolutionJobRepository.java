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
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** JDBC persistence owner for Skill Evolution jobs and generated patches. */
@Repository
public class JdbcSkillEvolutionJobRepository implements ISkillEvolutionJobRepository {

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
    @Transactional(transactionManager = "mysqlTransactionManager")
    public SkillEvolutionJobSnapshot enqueue(SkillEvolutionJobSnapshot job) {
        if (job == null) throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_REQUIRED");
        JdbcTemplate template = requiredTemplate();
        template.update("""
                        INSERT INTO ai_ops_skill_evolution_job
                          (job_id, run_id, session_id, project_id, agent_id, trigger_reason, status, attempts, next_run_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP)
                        ON DUPLICATE KEY UPDATE
                          run_id=VALUES(run_id),
                          session_id=VALUES(session_id),
                          project_id=VALUES(project_id),
                          agent_id=VALUES(agent_id),
                          trigger_reason=CASE
                            WHEN VALUES(trigger_reason)='USER_EXPLICIT_REMEMBER' THEN VALUES(trigger_reason)
                            WHEN trigger_reason='USER_EXPLICIT_REMEMBER' THEN trigger_reason
                            ELSE VALUES(trigger_reason)
                          END,
                          next_run_at=CURRENT_TIMESTAMP
                        """,
                job.jobId(),
                job.runId(),
                job.sessionId(),
                job.projectId(),
                job.agentId(),
                job.triggerReason(),
                job.status().name());
        return findJob(template, job.jobId())
                .orElseThrow(() -> new IllegalStateException("SKILL_EVOLUTION_ENQUEUED_JOB_MISSING"));
    }

    @Override
    public List<SkillEvolutionRunCandidate> findUnqueuedRunCandidates(int limit) {
        JdbcTemplate template = requiredTemplate();
        int bounded = Math.max(1, Math.min(limit, 500));
        return template.query("""
                SELECT r.run_id, r.session_id, r.project_id, r.agent_id, r.status, r.updated_at
                FROM ai_ops_agent_run r
                LEFT JOIN ai_ops_skill_evolution_job j ON j.run_id = r.run_id
                WHERE r.status IN ('SUCCEEDED','FAILED')
                  AND j.job_id IS NULL
                  AND r.project_id <> ''
                  AND r.agent_id <> ''
                ORDER BY r.updated_at ASC, r.run_id ASC
                LIMIT ?
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
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Optional<SkillEvolutionJobSnapshot> claimPending(int maxAttempts) {
        JdbcTemplate template = requiredTemplate();
        List<SkillEvolutionJobSnapshot> selected = template.query("""
                SELECT id, job_id, run_id, session_id, project_id, agent_id, trigger_reason, status,
                       attempts, next_run_at, last_error, create_time, update_time
                FROM ai_ops_skill_evolution_job
                WHERE status = 'PENDING'
                  AND (next_run_at IS NULL OR next_run_at <= CURRENT_TIMESTAMP)
                  AND attempts < ?
                ORDER BY id ASC
                LIMIT 1
                """, this::job, Math.max(1, maxAttempts));
        if (selected.isEmpty()) return Optional.empty();

        SkillEvolutionJobSnapshot candidate = selected.get(0);
        int updated = template.update("""
                UPDATE ai_ops_skill_evolution_job
                SET status = 'RUNNING', attempts = attempts + 1, update_time = CURRENT_TIMESTAMP
                WHERE job_id = ?
                  AND status = 'PENDING'
                  AND attempts = ?
                  AND (next_run_at IS NULL OR next_run_at <= CURRENT_TIMESTAMP)
                  AND attempts < ?
                """, candidate.jobId(), candidate.attempts(), Math.max(1, maxAttempts));
        return updated == 1 ? Optional.of(candidate) : Optional.empty();
    }

    @Override
    public List<SkillEvolutionJobSnapshot> findJobs(SkillEvolutionJobStatus status, int limit) {
        JdbcTemplate template = requiredTemplate();
        StringBuilder sql = new StringBuilder("""
                SELECT id, job_id, run_id, session_id, project_id, agent_id, trigger_reason, status,
                       attempts, next_run_at, last_error, create_time, update_time
                FROM ai_ops_skill_evolution_job
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(limit);
        return template.query(sql.toString(), this::job, args.toArray());
    }

    @Override
    public Optional<SkillEvolutionJobSnapshot> findJob(String jobId) {
        String id = value(jobId);
        if (id.isBlank()) return Optional.empty();
        return findJob(requiredTemplate(), id);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public SkillEvolutionPatchSnapshot savePatch(SkillEvolutionPatchSnapshot patch) {
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
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean complete(String jobId, SkillEvolutionJobStatus status) {
        if (status == null) throw new IllegalArgumentException("SKILL_EVOLUTION_TERMINAL_STATUS_REQUIRED");
        return requiredTemplate().update("""
                UPDATE ai_ops_skill_evolution_job
                SET status = ?, last_error = NULL, update_time = CURRENT_TIMESTAMP
                WHERE job_id = ?
                """, status.name(), value(jobId)) == 1;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean rescheduleOrFail(String jobId, SkillEvolutionRetryTransition transition, String lastError) {
        if (transition == null) throw new IllegalArgumentException("SKILL_EVOLUTION_RETRY_TRANSITION_REQUIRED");
        return requiredTemplate().update("""
                UPDATE ai_ops_skill_evolution_job
                SET status = ?, last_error = ?, next_run_at = ?, update_time = CURRENT_TIMESTAMP
                WHERE job_id = ?
                """,
                transition.status().name(),
                value(lastError),
                timestamp(transition.nextRunAt()),
                value(jobId)) == 1;
    }

    private Optional<SkillEvolutionJobSnapshot> findJob(JdbcTemplate template, String jobId) {
        return template.query("""
                        SELECT id, job_id, run_id, session_id, project_id, agent_id, trigger_reason, status,
                               attempts, next_run_at, last_error, create_time, update_time
                        FROM ai_ops_skill_evolution_job
                        WHERE job_id = ?
                        """,
                this::job,
                jobId).stream().findFirst();
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
                instant(resultSet.getTimestamp("update_time")));
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
