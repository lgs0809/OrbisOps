package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillReleasePort;
import cn.lgs.orbisops.domain.skill.model.SkillCanaryCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillFrozenCandidateSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC anti-corruption adapter for typed Skill release and canary facts. */
@Repository
public class JdbcSkillReleaseAdapter implements SkillReleasePort {

    // A frozen candidate keeps its version, but never keeps authority after its target is withdrawn.
    // CREATE has no catalog entry until publication; its eventual ID is deterministic.
    private static final String CURRENT_TARGET_JOIN = """
            LEFT JOIN ai_ops_skill s ON s.scope='PROJECT' AND s.project_id=r.project_id
              AND s.skill_id=CASE WHEN r.target_skill_id='' THEN CONCAT('evolved-',LEFT(c.candidate_hash,16))
                                 ELSE r.target_skill_id END
            """;
    private static final String CURRENT_TARGET_ALLOWED = """
            AND ((s.id IS NULL AND c.patch_type='CREATE' AND r.status='CANARY')
                 OR (s.lifecycle_status='ACTIVE' AND s.execution_mode='ENABLED'))
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillReleaseAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String,Object> observationContract(cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate candidate) {
        return new JdbcSkillCanaryContractReader(jdbcTemplate).freeze(candidate);
    }

    @Override
    public cn.lgs.orbisops.domain.skill.model.SkillCanaryEvidence canaryEvidence(SkillReleaseSnapshot release) {
        return new JdbcSkillCanaryEvidenceReader(jdbcTemplate).read(release);
    }

    @Override
    public Optional<SkillReleaseSnapshot> findByCandidate(String candidateId) {
        return jdbcTemplate.queryForList("""
                        SELECT *
                        FROM ai_ops_skill_release
                        WHERE candidate_id=?
                        LIMIT 1
                        """,
                candidateId).stream().findFirst().map(this::release);
    }

    @Override
    public void create(SkillReleaseSnapshot release) {
        if (release == null) throw new IllegalArgumentException("SKILL_RELEASE_REQUIRED");
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_release
                  (release_id,candidate_id,project_id,agent_id,target_skill_id,status,canary_percent,
                   baseline_version,baseline_skill_hash,reason_code,metadata_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE release_id=release_id
                """,
                release.releaseId(),
                release.candidateId(),
                release.projectId(),
                release.agentId(),
                release.targetSkillId(),
                release.status().name(),
                release.canaryPercent(),
                release.baselineVersion(),
                release.baselineSkillHash(),
                release.reasonCode(),
                JSON.toJSONString(release.metadata()));
    }

    @Override
    public List<SkillCanaryCandidateSnapshot> findCanaryCandidates(
            String projectId,
            String agentId,
            int limit) {
        return jdbcTemplate.queryForList("""
                SELECT r.*,c.candidate_hash,c.base_skill_version,c.base_skill_hash,
                       c.patch_type,c.changes_json,c.artifacts_json
                FROM ai_ops_skill_release r
                JOIN ai_ops_skill_patch_candidate c ON c.candidate_id=r.candidate_id AND c.project_id=r.project_id
                """ + CURRENT_TARGET_JOIN + """
                WHERE r.project_id=? AND (r.agent_id='' OR r.agent_id=?) AND r.status='CANARY'
                  AND c.scope='PROJECT' AND (c.agent_id='' OR c.agent_id=?)
                """ + CURRENT_TARGET_ALLOWED + """
                ORDER BY r.id DESC LIMIT ?
                """,
                projectId,
                agentId,
                agentId,
                Math.max(1, Math.min(limit, 20))).stream()
                .map(this::canaryCandidate)
                .toList();
    }

    @Override
    public Optional<SkillFrozenCandidateSnapshot> findFrozenCandidate(
            String candidateId,
            String projectId,
            String skillHash,
            String releaseId,
            String agentId) {
        return jdbcTemplate.queryForList("""
                SELECT c.candidate_id,c.project_id,c.agent_id,c.candidate_hash,
                       c.base_skill_version,c.changes_json
                FROM ai_ops_skill_patch_candidate c
                JOIN ai_ops_skill_release r ON r.candidate_id=c.candidate_id
                """ + CURRENT_TARGET_JOIN + """
                WHERE c.candidate_id=? AND c.project_id=? AND c.candidate_hash=?
                  AND r.release_id=? AND r.project_id=c.project_id AND r.status IN ('CANARY','ACTIVE')
                  AND c.scope='PROJECT' AND (c.agent_id='' OR c.agent_id=?) AND (r.agent_id='' OR r.agent_id=?)
                """ + CURRENT_TARGET_ALLOWED + """
                LIMIT 1
                """,
                candidateId,
                projectId,
                skillHash,
                releaseId,
                agentId,
                agentId).stream().findFirst().map(this::frozenCandidate);
    }

    @Override
    public List<SkillReleaseSnapshot> listEvaluable(int limit) {
        var reasons=new cn.lgs.orbisops.domain.skill.service.SkillPublicationAdmissionPolicy().retainedStaleReasons();
        var parameters=new java.util.ArrayList<Object>(reasons);
        parameters.add(Math.max(1, Math.min(limit, 100)));
        return jdbcTemplate.queryForList("""
                SELECT *
                FROM ai_ops_skill_release
                WHERE (status IN ('READY','PENDING_INDEX','CANARY')
                   OR (status='ACTIVE' AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.publicationPolicy')),'') <> 'source-qualified-auto-v1'))
                  AND (COALESCE(JSON_EXTRACT(metadata_json,'$.retryAfterMs'),0) <= UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000
                    OR (status='READY'
                      AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.publicationPolicy'))='source-qualified-auto-v1'
                      AND reason_code IN (%s)))
                ORDER BY update_time ASC,id ASC LIMIT ?
                """.formatted(String.join(",",java.util.Collections.nCopies(reasons.size(),"?"))),
                parameters.toArray()).stream()
                .map(this::release)
                .filter(item -> item.status().evaluable())
                .toList();
    }

    @Override
    public boolean claim(
            String releaseId,
            SkillReleaseStatus fromStatus,
            SkillReleaseStatus toStatus) {
        requireTransition(fromStatus, toStatus);
        return jdbcTemplate.update("""
                        UPDATE ai_ops_skill_release
                        SET status=?,update_time=CURRENT_TIMESTAMP
                        WHERE release_id=? AND status=?
                        """,
                toStatus.name(),
                releaseId,
                fromStatus.name()) == 1;
    }

    @Override
    public boolean complete(
            String releaseId,
            SkillReleaseStatus expectedStatus,
            SkillReleaseStatus status,
            String reasonCode,
            int releasedVersion,
            String releasedSkillHash,
            String targetSkillId) {
        requireTransition(expectedStatus, status);
        return jdbcTemplate.update("""
                        UPDATE ai_ops_skill_release
                        SET status=?,reason_code=?,released_version=?,released_skill_hash=?,
                            target_skill_id=?,update_time=CURRENT_TIMESTAMP
                        WHERE release_id=? AND status=?
                        """,
                status.name(),
                reasonCode,
                releasedVersion,
                releasedSkillHash,
                targetSkillId,
                releaseId,
                expectedStatus.name()) == 1;
    }

    @Override
    public void markReconciliationRequired(
            String releaseId,
            String reasonCode) {
        jdbcTemplate.update("""
                UPDATE ai_ops_skill_release
                SET status=CASE
                      WHEN status='PROMOTING' THEN 'PROMOTION_UNKNOWN'
                      WHEN status='ROLLING_BACK' THEN 'ROLLBACK_UNKNOWN'
                      ELSE status
                    END,
                    metadata_json=CASE WHEN status IN ('READY','PENDING_INDEX') THEN
                      JSON_SET(COALESCE(metadata_json,'{}'),
                        '$.retryAfterMs',UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 +
                          LEAST(3600000,60000*POW(2,LEAST(6,COALESCE(JSON_EXTRACT(metadata_json,'$.retryAttempts'),0)))),
                        '$.retryAttempts',COALESCE(JSON_EXTRACT(metadata_json,'$.retryAttempts'),0)+1)
                      ELSE metadata_json END,
                    reason_code=?,update_time=CURRENT_TIMESTAMP
                WHERE release_id=?
                """,
                reasonCode == null ? "" : reasonCode.substring(0, Math.min(128, reasonCode.length())),
                releaseId);
    }

    private SkillReleaseSnapshot release(Map<String, Object> row) {
        return new SkillReleaseSnapshot(
                text(row.get("release_id")),
                text(row.get("candidate_id")),
                text(row.get("project_id")),
                text(row.get("agent_id")),
                text(row.get("target_skill_id")),
                SkillReleaseStatus.require(text(row.get("status"))),
                integer(row.get("canary_percent")),
                integer(row.get("baseline_version")),
                text(row.get("baseline_skill_hash")),
                text(row.get("reason_code")),
                integer(row.get("released_version")),
                text(row.get("released_skill_hash")),
                object(row.get("metadata_json")));
    }

    private SkillCanaryCandidateSnapshot canaryCandidate(Map<String, Object> row) {
        return new SkillCanaryCandidateSnapshot(
                text(row.get("release_id")),
                text(row.get("candidate_id")),
                text(row.get("project_id")),
                text(row.get("agent_id")),
                text(row.get("target_skill_id")),
                SkillReleaseStatus.require(text(row.get("status"))),
                text(row.get("candidate_hash")),
                integer(row.get("base_skill_version")),
                text(row.get("base_skill_hash")),
                text(row.get("patch_type")),
                text(row.get("changes_json")),
                text(row.get("artifacts_json")));
    }

    private SkillFrozenCandidateSnapshot frozenCandidate(Map<String, Object> row) {
        return new SkillFrozenCandidateSnapshot(
                text(row.get("candidate_id")),
                text(row.get("project_id")),
                text(row.get("agent_id")),
                text(row.get("candidate_hash")),
                integer(row.get("base_skill_version")),
                text(row.get("changes_json")));
    }

    private void requireTransition(
            SkillReleaseStatus fromStatus,
            SkillReleaseStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            throw new IllegalArgumentException("SKILL_RELEASE_TRANSITION_STATUS_REQUIRED");
        }
        fromStatus.requireTransitionTo(toStatus);
    }

    private Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        String json = text(value);
        if (json.isBlank()) return Map.of();
        Map<String, Object> parsed = JSON.parseObject(
                json,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        return parsed == null ? Map.of() : parsed;
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
