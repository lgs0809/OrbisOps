package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillCandidateTournamentContext;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSet;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSetPort;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteDraft;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuitePort;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSuiteSnapshot;
import cn.lgs.orbisops.application.skill.SkillTournamentCandidate;
import cn.lgs.orbisops.domain.evidence.service.SensitiveDataRedactionPolicy;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Versioned hidden/mutation evaluation suite store used by strict Skill tournaments. */
@Repository
public class JdbcSkillHiddenEvaluationSuiteAdapter
        implements SkillHiddenEvaluationSetPort, SkillHiddenEvaluationSuitePort {

    private static final SensitiveDataRedactionPolicy REDACTION =
            new SensitiveDataRedactionPolicy();

    private final JdbcTemplate jdbc;
    private final boolean autoInit;

    public JdbcSkillHiddenEvaluationSuiteAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider,
            @Value("${orbisops.skill-evolution.tournament.hidden-suite-auto-init:true}")
            boolean autoInit) {
        this.jdbc = provider.getIfAvailable();
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (jdbc == null || !autoInit) return;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_hidden_eval_suite (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  suite_id VARCHAR(96) NOT NULL,
                  project_id VARCHAR(128) NOT NULL,
                  skill_id VARCHAR(128) NOT NULL,
                  base_version BIGINT NOT NULL,
                  base_skill_hash VARCHAR(64) NOT NULL,
                  suite_version VARCHAR(64) NOT NULL,
                  hidden_cases_json MEDIUMTEXT NOT NULL,
                  mutation_cases_json MEDIUMTEXT NOT NULL,
                  hidden_eval_hash VARCHAR(64) NOT NULL,
                  mutation_eval_hash VARCHAR(64) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  actor VARCHAR(128) NOT NULL,
                  created_at DATETIME(6) NOT NULL,
                  updated_at DATETIME(6) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_hidden_suite_id (suite_id),
                  UNIQUE KEY uk_skill_hidden_suite_target
                    (project_id, skill_id, base_version, base_skill_hash, suite_version),
                  KEY idx_skill_hidden_suite_lookup
                    (project_id, skill_id, status, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                  COMMENT='Skill Strict Tournament 隐藏与变异评估套件'
                """);
    }

    @Override
    public SkillHiddenEvaluationSet load(
            String skillId,
            long baseVersion,
            SkillTournamentCandidate candidate) {
        throw new IllegalStateException("SKILL_HIDDEN_EVAL_CONTEXT_REQUIRED");
    }

    @Override
    public SkillHiddenEvaluationSet load(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate) {
        if (context == null || candidate == null) {
            throw new IllegalArgumentException("SKILL_HIDDEN_EVAL_INPUT_REQUIRED");
        }
        StoredSuite suite = requireStored(
                context.projectId(),
                context.skillId(),
                context.baseVersion(),
                context.baseSkillHash(),
                context.hiddenSuiteVersion());
        assertHashes(suite);
        return new SkillHiddenEvaluationSet(
                suite.snapshot().suiteId(),
                suite.hiddenCases(),
                suite.mutationCases(),
                suite.snapshot().hiddenEvalHash(),
                suite.snapshot().mutationEvalHash());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public SkillHiddenEvaluationSuiteSnapshot publish(
            SkillHiddenEvaluationSuiteDraft draft) {
        if (draft == null) throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_DRAFT_REQUIRED");
        assertNoSensitiveData(draft.hiddenCases());
        assertNoSensitiveData(draft.mutationCases());
        Instant now = Instant.now();
        try {
            requireJdbc().update("""
                        INSERT INTO ai_ops_skill_hidden_eval_suite (
                          suite_id, project_id, skill_id, base_version, base_skill_hash,
                          suite_version, hidden_cases_json, mutation_cases_json,
                          hidden_eval_hash, mutation_eval_hash, status, actor,
                          created_at, updated_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?, 'ACTIVE',?,?,?)
                        """,
                draft.suiteId(),
                draft.projectId(),
                draft.skillId(),
                draft.baseVersion(),
                draft.baseSkillHash(),
                draft.suiteVersion(),
                JSON.toJSONString(draft.hiddenCases()),
                JSON.toJSONString(draft.mutationCases()),
                draft.hiddenEvalHash(),
                draft.mutationEvalHash(),
                draft.actor(),
                Timestamp.from(now),
                Timestamp.from(now));
        } catch (DuplicateKeyException duplicate) {
            SkillHiddenEvaluationSuiteSnapshot existing = find(
                    draft.projectId(),
                    draft.skillId(),
                    draft.baseVersion(),
                    draft.baseSkillHash(),
                    draft.suiteVersion()).orElseThrow(() -> duplicate);
            if (!existing.suiteId().equals(draft.suiteId())
                    || !existing.hiddenEvalHash().equals(draft.hiddenEvalHash())
                    || !existing.mutationEvalHash().equals(draft.mutationEvalHash())) {
                throw new SecurityException("SKILL_HIDDEN_EVAL_IMMUTABLE_CONFLICT", duplicate);
            }
            return existing;
        }
        return find(
                draft.projectId(),
                draft.skillId(),
                draft.baseVersion(),
                draft.baseSkillHash(),
                draft.suiteVersion()).orElseThrow(() -> new IllegalStateException(
                "SKILL_HIDDEN_SUITE_MISSING_AFTER_PUBLISH:" + draft.suiteId()));
    }

    @Override
    public Optional<SkillHiddenEvaluationSuiteSnapshot> find(
            String projectId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            String suiteVersion) {
        List<StoredSuite> rows = query(
                projectId, skillId, baseVersion, baseSkillHash, suiteVersion);
        if (rows.size() > 1) {
            throw new IllegalStateException("SKILL_HIDDEN_SUITE_DUPLICATE");
        }
        return rows.stream().findFirst().map(StoredSuite::snapshot);
    }

    @Override
    public List<SkillHiddenEvaluationSuiteSnapshot> list(
            String projectId,
            String skillId,
            int limit) {
        String project = required(projectId, "SKILL_HIDDEN_SUITE_PROJECT_ID_REQUIRED");
        String skill = required(skillId, "SKILL_HIDDEN_SUITE_SKILL_ID_REQUIRED");
        return requireJdbc().query("""
                        SELECT suite_id, project_id, skill_id, base_version, base_skill_hash,
                               suite_version, hidden_cases_json, mutation_cases_json,
                               hidden_eval_hash, mutation_eval_hash, status, actor,
                               created_at, updated_at
                          FROM ai_ops_skill_hidden_eval_suite
                         WHERE project_id=? AND skill_id=?
                         ORDER BY updated_at DESC, id DESC
                         LIMIT ?
                        """,
                (rs, rowNum) -> row(rs).snapshot(),
                project,
                skill,
                Math.max(1, Math.min(limit, 200)));
    }

    private StoredSuite requireStored(
            String projectId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            String suiteVersion) {
        List<StoredSuite> rows = query(
                projectId, skillId, baseVersion, baseSkillHash, suiteVersion);
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "SKILL_HIDDEN_EVAL_SET_NOT_FOUND:"
                            + projectId + ":" + skillId + ":" + baseVersion + ":" + suiteVersion);
        }
        if (rows.size() > 1) throw new IllegalStateException("SKILL_HIDDEN_SUITE_DUPLICATE");
        return rows.get(0);
    }

    private List<StoredSuite> query(
            String projectId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            String suiteVersion) {
        String project = required(projectId, "SKILL_HIDDEN_SUITE_PROJECT_ID_REQUIRED");
        String skill = required(skillId, "SKILL_HIDDEN_SUITE_SKILL_ID_REQUIRED");
        if (baseVersion <= 0) throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_BASE_VERSION_INVALID");
        String baseHash = hash(baseSkillHash, "SKILL_HIDDEN_SUITE_BASE_HASH_INVALID");
        String version = required(suiteVersion, "SKILL_HIDDEN_SUITE_VERSION_REQUIRED");
        return requireJdbc().query("""
                        SELECT suite_id, project_id, skill_id, base_version, base_skill_hash,
                               suite_version, hidden_cases_json, mutation_cases_json,
                               hidden_eval_hash, mutation_eval_hash, status, actor,
                               created_at, updated_at
                          FROM ai_ops_skill_hidden_eval_suite
                         WHERE project_id=? AND skill_id=? AND base_version=?
                           AND base_skill_hash=? AND suite_version=? AND status='ACTIVE'
                        """,
                (rs, rowNum) -> row(rs),
                project,
                skill,
                baseVersion,
                baseHash,
                version);
    }

    private StoredSuite row(ResultSet rs) throws SQLException {
        SkillHiddenEvaluationSuiteSnapshot snapshot = new SkillHiddenEvaluationSuiteSnapshot(
                rs.getString("suite_id"),
                rs.getString("project_id"),
                rs.getString("skill_id"),
                rs.getLong("base_version"),
                rs.getString("base_skill_hash"),
                rs.getString("suite_version"),
                rs.getString("hidden_eval_hash"),
                rs.getString("mutation_eval_hash"),
                rs.getString("status"),
                rs.getString("actor"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
        return new StoredSuite(
                snapshot,
                maps(rs.getString("hidden_cases_json")),
                maps(rs.getString("mutation_cases_json")));
    }

    private void assertHashes(StoredSuite suite) {
        if (!suite.snapshot().hiddenEvalHash().equals(
                CanonicalObjectHasher.sha256(suite.hiddenCases()))) {
            throw new SecurityException("SKILL_HIDDEN_EVAL_HASH_MISMATCH");
        }
        if (!suite.snapshot().mutationEvalHash().equals(
                CanonicalObjectHasher.sha256(suite.mutationCases()))) {
            throw new SecurityException("SKILL_MUTATION_EVAL_HASH_MISMATCH");
        }
    }

    private void assertNoSensitiveData(List<Map<String, Object>> cases) {
        Object redacted = REDACTION.redact(cases);
        if (!CanonicalObjectHasher.sha256(cases).equals(
                CanonicalObjectHasher.sha256(redacted))) {
            throw new SecurityException("SKILL_HIDDEN_SUITE_SENSITIVE_DATA_FORBIDDEN");
        }
    }

    private List<Map<String, Object>> maps(String json) {
        if (json == null || json.isBlank()) return List.of();
        Object parsed = JSON.parse(json);
        if (!(parsed instanceof Iterable<?> iterable)) {
            throw new IllegalStateException("SKILL_HIDDEN_SUITE_JSON_INVALID");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> source)) {
                throw new IllegalStateException("SKILL_HIDDEN_SUITE_CASE_INVALID");
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(String.valueOf(key), value));
            result.add(Collections.unmodifiableMap(copy));
        }
        return List.copyOf(result);
    }

    private JdbcTemplate requireJdbc() {
        if (jdbc == null) throw new IllegalStateException("SKILL_HIDDEN_SUITE_STORE_UNAVAILABLE");
        return jdbc;
    }

    private Instant instant(Timestamp value) {
        if (value == null) throw new IllegalStateException("SKILL_HIDDEN_SUITE_TIME_MISSING");
        return value.toInstant();
    }

    private String hash(String value, String reasonCode) {
        String normalized = required(value, reasonCode).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private record StoredSuite(
            SkillHiddenEvaluationSuiteSnapshot snapshot,
            List<Map<String, Object>> hiddenCases,
            List<Map<String, Object>> mutationCases) {
    }
}
