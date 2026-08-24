package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillPatchCandidatePort;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillPatchRiskLevel;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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
    @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public SkillPatchCandidate createEvolution(SkillPatchCandidate candidate,SkillEvolutionJobSnapshot claim,String sourceHash,String planId,String planHash) {
        if (claim==null || candidate==null || !claim.projectId().equals(candidate.projectId())
                || !claim.agentId().equals(candidate.agentId()) || !claim.runId().equals(candidate.sourceRunId()))
            throw new IllegalArgumentException("SKILL_EVOLUTION_CANDIDATE_SCOPE_MISMATCH");
        var proposals=new JdbcSkillEvolutionProposalAdapter(jdbcTemplate);
        var plan=proposals.current(claim,planId,planHash,sourceHash);
        if(plan.authored().isEmpty()) throw new IllegalStateException("SKILL_EVOLUTION_AUTHORED_RESULT_REQUIRED");
        requireAuthoredPayload(candidate,plan.authored());
        new JdbcSkillEvolutionRelatedSkillGuard(jdbcTemplate).requireTarget(plan,candidate);
        new JdbcSkillNovelSourceGuard(jdbcTemplate).requireNovel(plan,candidate);
        var source=new JdbcSkillEvolutionSourceReader(jdbcTemplate).load(claim);
        if (source.sourceHash().isBlank() || !source.sourceHash().equals(sourceHash))
            throw new IllegalStateException("SKILL_EVOLUTION_CANDIDATE_SOURCE_CHANGED");
        String existing=jdbcTemplate.queryForObject("SELECT candidate_id FROM ai_ops_skill_evolution_source WHERE source_id=? FOR UPDATE",String.class,claim.sourceId());
        if (existing!=null && !existing.isBlank()) return get(existing);
        proposals.reserveAssets(plan,candidate);
        var stored=create(candidate);
        jdbcTemplate.update("UPDATE ai_ops_skill_evolution_source SET candidate_id=? WHERE source_id=?",stored.candidateId(),claim.sourceId());
        return stored;
    }

    void requireAuthoredPayload(SkillPatchCandidate candidate,Map<String,Object> authored) {
        if(!candidate.targetSkillId().equals(text(authored.get("targetSkillId"))))
            throw new IllegalStateException("SKILL_EVOLUTION_AUTHORED_TARGET_CHANGED");
        String risk=text(authored.get("riskLevel"));if(risk.isBlank()) risk="LOW";
        if(!candidate.patchType().equals(text(authored.get("patchType"))) || !candidate.riskLevel().name().equals(risk)
                || !canonical(candidate.changes()).equals(canonical(authored.getOrDefault("changes",List.of())))
                || !canonical(behaviorArtifacts(candidate.artifacts())).equals(canonical(behaviorArtifacts(authored.get("artifacts")))))
            throw new IllegalStateException("SKILL_EVOLUTION_AUTHORED_CONTENT_CHANGED");
    }
    private List<?> behaviorArtifacts(Object value) {
        if(!(value instanceof List<?> list)) return List.of();
        // This development-only artifact is generated deterministically after the author response.
        return list.stream().filter(x->!(x instanceof Map<?,?> m) || !"evals/routing-boundary-cases.json".equals(m.get("path"))).toList();
    }
    private String canonical(Object value) { return cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(value); }

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
