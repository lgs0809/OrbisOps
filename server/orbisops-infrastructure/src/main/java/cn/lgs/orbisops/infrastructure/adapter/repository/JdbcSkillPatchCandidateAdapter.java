package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillPatchCandidatePort;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillPatchRiskLevel;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** JDBC anti-corruption adapter for typed Skill patch candidates. */
@Repository
public class JdbcSkillPatchCandidateAdapter
        implements SkillPatchCandidatePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillPatchCandidateAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SkillPatchCandidate create(SkillPatchCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_REQUIRED");
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_patch_candidate
                  (candidate_id,candidate_hash,source_run_id,source_type,project_id,agent_id,scope,target_skill_id,
                   patch_type,risk_level,base_skill_version,base_skill_hash,context_bundle_hash,evidence_refs_json,
                   changes_json,artifacts_json,eval_cases_json,status,reason_code)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE candidate_id=candidate_id
                """,
                candidate.candidateId(),
                candidate.candidateHash(),
                candidate.sourceRunId(),
                candidate.sourceType(),
                candidate.projectId(),
                candidate.agentId(),
                candidate.scope(),
                candidate.targetSkillId(),
                candidate.patchType(),
                candidate.riskLevel().name(),
                candidate.baseSkillVersion(),
                candidate.baseSkillHash(),
                candidate.contextBundleHash(),
                listJson(candidate.evidenceRefs()),
                listJson(candidate.changes()),
                listJson(candidate.artifacts()),
                listJson(candidate.evalCases()),
                candidate.status().name(),
                candidate.reasonCode());
        return map(jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM ai_ops_skill_patch_candidate
                        WHERE project_id=? AND candidate_hash=?
                        """,
                candidate.projectId(),
                candidate.candidateHash()));
    }

    @Override
    public SkillPatchCandidate get(String candidateId) {
        return map(jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM ai_ops_skill_patch_candidate
                        WHERE candidate_id=?
                        """,
                candidateId));
    }

    @Override
    public boolean transition(
            String candidateId,
            SkillPatchCandidateStatus fromStatus,
            SkillPatchCandidateStatus toStatus,
            String reasonCode) {
        if (fromStatus == null || toStatus == null) {
            throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_STATUS_REQUIRED");
        }
        return jdbcTemplate.update("""
                        UPDATE ai_ops_skill_patch_candidate
                        SET status=?, reason_code=?, update_time=CURRENT_TIMESTAMP
                        WHERE candidate_id=? AND status=?
                        """,
                toStatus.name(),
                text(reasonCode),
                candidateId,
                fromStatus.name()) == 1;
    }

    @Override
    public void updateStatus(
            String candidateId,
            SkillPatchCandidateStatus status,
            String reasonCode) {
        if (status == null) throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_STATUS_REQUIRED");
        jdbcTemplate.update("""
                        UPDATE ai_ops_skill_patch_candidate
                        SET status=?, reason_code=?, update_time=CURRENT_TIMESTAMP
                        WHERE candidate_id=?
                        """,
                status.name(),
                text(reasonCode),
                candidateId);
    }

    private SkillPatchCandidate map(Map<String, Object> row) {
        return new SkillPatchCandidate(
                text(row.get("candidate_id")),
                text(row.get("candidate_hash")),
                text(row.get("source_run_id")),
                text(row.get("source_type")),
                text(row.get("project_id")),
                text(row.get("agent_id")),
                text(row.get("scope")),
                text(row.get("target_skill_id")),
                text(row.get("patch_type")),
                SkillPatchRiskLevel.require(text(row.get("risk_level"))),
                number(row.get("base_skill_version")),
                text(row.get("base_skill_hash")),
                text(row.get("context_bundle_hash")),
                parseList(row.get("evidence_refs_json")),
                parseList(row.get("changes_json")),
                parseList(row.get("artifacts_json")),
                parseList(row.get("eval_cases_json")),
                SkillPatchCandidateStatus.require(text(row.get("status"))),
                text(row.get("reason_code")),
                localDateTime(row.get("create_time")),
                localDateTime(row.get("update_time")));
    }

    private List<Object> parseList(Object value) {
        String json = text(value);
        if (json.isBlank() || "null".equalsIgnoreCase(json)) return List.of();
        try {
            List<Object> parsed = JSON.parseArray(json, Object.class);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private String listJson(Object value) {
        return JSON.toJSONString(value instanceof Iterable<?> ? value : List.of());
    }

    private int number(Object value) {
        try {
            return value instanceof Number number
                    ? Math.max(0, number.intValue())
                    : Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
