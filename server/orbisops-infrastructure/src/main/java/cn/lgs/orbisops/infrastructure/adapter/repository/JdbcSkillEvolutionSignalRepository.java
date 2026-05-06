package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionSignalRepository;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** JDBC persistence owner for Skill Evolution signals and hints. */
@Repository
public class JdbcSkillEvolutionSignalRepository implements ISkillEvolutionSignalRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcSkillEvolutionSignalRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public SkillEvolutionSignalSnapshot saveIdempotent(SkillEvolutionSignalSnapshot signal) {
        if (signal == null) throw new IllegalArgumentException("SKILL_SIGNAL_REQUIRED");
        JdbcTemplate template = requiredTemplate("Skill Evolution Signal Store 未配置");
        template.update("""
                INSERT INTO ai_ops_skill_evolution_signal
                  (signal_id,idempotency_key,project_id,agent_id,run_id,session_id,signal_type,payload_json,status)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE signal_id=signal_id
                """,
                signal.signalId(),
                signal.idempotencyKey(),
                signal.projectId(),
                signal.agentId(),
                signal.runId(),
                signal.sessionId(),
                signal.signalType(),
                signal.payloadJson(),
                signal.status());
        return template.query("""
                        SELECT signal_id,idempotency_key,project_id,agent_id,run_id,session_id,
                               signal_type,payload_json,status,created_at
                        FROM ai_ops_skill_evolution_signal
                        WHERE idempotency_key=?
                        """,
                this::signal,
                signal.idempotencyKey()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("SKILL_SIGNAL_IDEMPOTENT_READ_MISSING"));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void saveHintIdempotent(SkillEvolutionHintSnapshot hint) {
        if (hint == null) throw new IllegalArgumentException("SKILL_HINT_REQUIRED");
        JdbcTemplate template = requiredTemplate("Skill Evolution Hint Store 未配置");
        template.update("""
                INSERT INTO ai_ops_skill_evolution_hint
                  (hint_id,signal_id,project_id,run_id,hint_type,content_json,status)
                VALUES (?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE hint_id=hint_id
                """,
                hint.hintId(),
                hint.signalId(),
                hint.projectId(),
                hint.runId(),
                hint.hintType(),
                hint.contentJson(),
                hint.status());
    }

    @Override
    public List<SkillEvolutionHintSnapshot> findPendingHints(String projectId, int limit) {
        String project = value(projectId);
        if (project.isBlank()) return List.of();
        JdbcTemplate template = requiredTemplate("Skill Evolution Hint Store 未配置");
        return template.query("""
                SELECT hint_id,signal_id,project_id,run_id,hint_type,content_json,status,created_at
                FROM ai_ops_skill_evolution_hint
                WHERE project_id=? AND status='CREATED'
                ORDER BY id DESC
                LIMIT ?
                """, this::hint, project, limit);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean markHintConsumed(String hintId) {
        String id = value(hintId);
        if (id.isBlank()) return false;
        JdbcTemplate template = requiredTemplate("Skill Evolution Hint Store 未配置");
        return template.update("""
                UPDATE ai_ops_skill_evolution_hint
                SET status='CONSUMED'
                WHERE hint_id=? AND status='CREATED'
                """, id) == 1;
    }

    private SkillEvolutionSignalSnapshot signal(ResultSet resultSet, int rowNum) throws SQLException {
        return new SkillEvolutionSignalSnapshot(
                resultSet.getString("signal_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("project_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("run_id"),
                resultSet.getString("session_id"),
                resultSet.getString("signal_type"),
                resultSet.getString("payload_json"),
                resultSet.getString("status"),
                instant(resultSet.getTimestamp("created_at")));
    }

    private SkillEvolutionHintSnapshot hint(ResultSet resultSet, int rowNum) throws SQLException {
        return new SkillEvolutionHintSnapshot(
                resultSet.getString("hint_id"),
                resultSet.getString("signal_id"),
                resultSet.getString("project_id"),
                resultSet.getString("run_id"),
                resultSet.getString("hint_type"),
                resultSet.getString("content_json"),
                resultSet.getString("status"),
                instant(resultSet.getTimestamp("created_at")));
    }

    private JdbcTemplate requiredTemplate(String error) {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException(error);
        return template;
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
