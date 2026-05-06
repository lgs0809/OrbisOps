package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

@Repository
public class JdbcSkillEvolutionDiagnostic implements SkillEvolutionDiagnosticPort {
    private final JdbcTemplate jdbc;
    public JdbcSkillEvolutionDiagnostic(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public java.util.Optional<AuthoredDecision> authoredDecision(String projectId,String jobId,String sourceId) {
        if(projectId==null || projectId.isBlank() || jobId==null || jobId.isBlank() || sourceId==null || sourceId.isBlank())
            return java.util.Optional.empty();
        var rows=jdbc.queryForList("""
                SELECT p.plan_id,p.authored_json,
                  (e.revision=f.episode_revision AND e.verified_outcome_ref=f.source_id AND e.outcome='SUCCEEDED') AS current_source
                FROM ai_ops_skill_evolution_job j
                JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                JOIN ai_ops_skill_evolution_proposal p ON p.project_id=j.project_id AND p.job_id=j.job_id AND p.source_id=s.source_id
                JOIN ai_ops_skill_evolution_source f ON f.project_id=j.project_id AND f.source_id=s.source_id
                JOIN ai_ops_task_episode e ON e.project_id=f.project_id AND e.episode_id=f.episode_id
                WHERE j.project_id=? AND j.job_id=? AND s.source_id=?
                  AND SHA2(p.input_json,256)=p.plan_hash AND SHA2(p.authored_json,256)=p.authored_hash
                  AND SHA2(f.input_json,256)=f.input_hash
                  AND JSON_UNQUOTE(JSON_EXTRACT(p.input_json,'$.evolutionSourceHash'))=f.input_hash
                """,projectId,jobId,sourceId);
        if(rows.size()!=1) return java.util.Optional.empty();
        var row=rows.get(0);
        // Only authored product fields are projected. Provider payloads, inputs and artifacts remain in the audit.
        var authored=cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate.from(
                CanonicalJson.parseObject(String.valueOf(row.get("authored_json"))));
        return java.util.Optional.of(new AuthoredDecision(String.valueOf(row.get("plan_id")),authored.patchType(),
                authored.reason(),authored.source(),String.valueOf(authored.payload().getOrDefault("authoringModel","")),
                ((Number)row.get("current_source")).intValue()==1));
    }
    @Override public Map<String,Object> authoredPublication(String projectId,String jobId,String sourceId) {
        if(projectId==null || projectId.isBlank() || jobId==null || jobId.isBlank() || sourceId==null || sourceId.isBlank())
            return Map.of();
        var rows=jdbc.queryForList("""
                SELECT c.candidate_id,COALESCE(a.status,r.status,c.status) AS publication_status,
                  COALESCE(a.operation,c.patch_type) AS operation,COALESCE(r.released_version,0) AS released_version,
                  r.reason_code
                FROM ai_ops_skill_evolution_job j
                JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                JOIN ai_ops_skill_evolution_proposal p ON p.project_id=j.project_id AND p.job_id=j.job_id AND p.source_id=s.source_id
                JOIN ai_ops_skill_patch_candidate c ON c.project_id=p.project_id AND c.candidate_id=p.candidate_id
                LEFT JOIN ai_ops_skill_release r ON r.project_id=c.project_id AND r.candidate_id=c.candidate_id
                LEFT JOIN ai_ops_skill_atomic_publication a ON a.project_id=c.project_id AND a.candidate_id=c.candidate_id
                WHERE j.project_id=? AND j.job_id=? AND s.source_id=?
                ORDER BY p.create_time DESC,p.plan_id DESC LIMIT 1
                """,projectId,jobId,sourceId);
        if(rows.isEmpty()) return Map.of();
        var row=rows.get(0);
        String reason=String.valueOf(row.get("reason_code"));
        String prefix="POLICY_PUBLICATION_BASELINE_STALE:";
        boolean stale=reason.startsWith(prefix) && new cn.lgs.orbisops.domain.skill.service.SkillPublicationAdmissionPolicy()
                .stale(new IllegalStateException(reason.substring(prefix.length())));
        // Provider diagnostics and proposal bodies stay in the original authorized audit.
        return Map.of("candidateId",row.get("candidate_id"),"status",row.get("publication_status"),
                "operation",row.get("operation"),"releasedVersion",row.get("released_version"),
                "reason",stale?"BASELINE_STALE":"");
    }
    @Override public Map<String,Object> savedExperience(String projectId,String jobId,String sourceId) {
        if(projectId==null || projectId.isBlank() || jobId==null || jobId.isBlank() || sourceId==null || sourceId.isBlank())
            return Map.of();
        // Join all identities; another analysis of the same Run must not borrow this task's experience.
        var rows=jdbc.queryForList("""
                SELECT f.method_json,f.method_hash,f.group_id,f.status,f.episode_revision,
                  (e.revision=f.episode_revision AND e.verified_outcome_ref=f.source_id AND e.outcome='SUCCEEDED') AS current_source
                FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                JOIN ai_ops_skill_method_experience f ON f.project_id=j.project_id AND f.source_id=s.source_id
                JOIN ai_ops_task_episode e ON e.project_id=f.project_id AND e.episode_id=f.episode_id
                WHERE j.project_id=? AND j.job_id=? AND s.source_id=?
                """,projectId,jobId,sourceId);
        if(rows.isEmpty()) return Map.of();
        var row=rows.get(0);
        return Map.of("sourceId",sourceId,"method",CanonicalJson.parseObject(String.valueOf(row.get("method_json"))),
                "methodHash",row.get("method_hash"),"groupId",row.get("group_id"),"status",row.get("status"),
                "episodeRevision",row.get("episode_revision"),"currentSource",((Number)row.get("current_source")).intValue()==1);
    }
    @Override public Map<PublicationRef, PublicationState> publications(java.util.Collection<PublicationRef> refs) {
        if (refs.isEmpty()) return Map.of();
        var parameters = new java.util.ArrayList<Object>();
        var predicates = new java.util.ArrayList<String>();
        for (var ref : new java.util.LinkedHashSet<>(refs)) {
            predicates.add("(c.project_id=? AND c.candidate_id=?)");
            parameters.add(ref.projectId()); parameters.add(ref.candidateId());
        }
        var result = new java.util.LinkedHashMap<PublicationRef, PublicationState>();
        jdbc.query("""
                SELECT c.project_id,c.candidate_id,COALESCE(a.status,r.status,c.status) AS publication_status,
                  COALESCE(a.operation,c.patch_type) AS operation,COALESCE(r.target_skill_id,c.target_skill_id) AS target_skill_id,
                  COALESCE(r.released_version,0) AS released_version,a.plan_json
                FROM ai_ops_skill_patch_candidate c
                LEFT JOIN ai_ops_skill_release r ON r.project_id=c.project_id AND r.candidate_id=c.candidate_id
                LEFT JOIN ai_ops_skill_atomic_publication a ON a.project_id=c.project_id AND a.candidate_id=c.candidate_id
                WHERE """ + String.join(" OR ", predicates), rs -> {
            String plan = rs.getString("plan_json");
            var targets = new java.util.ArrayList<String>();
            if (plan != null) {
                Object members = CanonicalJson.parseObject(plan).get("targets");
                if (members instanceof List<?> list) for (Object member : list)
                    if (member instanceof Map<?,?> value && value.get("skillId") instanceof String id) targets.add(id);
            } else {
                String target = rs.getString("target_skill_id");
                if (target != null && !target.isBlank()) targets.add(target);
            }
            result.put(new PublicationRef(rs.getString("project_id"),rs.getString("candidate_id")),
                    new PublicationState(rs.getString("publication_status"),rs.getString("operation"),
                            List.copyOf(targets),rs.getInt("released_version")));
        }, parameters.toArray());
        return Map.copyOf(result);
    }
    @Override public String currentFailure(String projectId,String jobId,int attempt) {
        if(projectId==null || projectId.isBlank() || jobId==null || jobId.isBlank() || attempt<1) return "";
        var errors=jdbc.queryForList("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(after_json,'$.error')) FROM ai_ops_config_audit
                WHERE project_id=? AND target_id=? AND module_name='skill-evolver' AND action_name='job-fail'
                  AND JSON_EXTRACT(after_json,'$.attempts')=? ORDER BY id DESC LIMIT 1
                """,String.class,projectId,jobId,attempt);
        return errors.isEmpty()?"":classify(errors.get(0));
    }
    static String classify(String error) {
        if(error==null) return "";
        for(String code:List.of("SKILL_MODEL_TRANSPORT_DEFERRED","SKILL_EVIDENCE_INPUT_DEFERRED","SKILL_AUTHORING_INPUT_TOO_LARGE","SKILL_AUTHORING_SOURCE_TOO_LARGE",
                "SKILL_AUTHORING_MODEL_INVALID","SKILL_EVOLUTION_SOURCE_TOO_LARGE","RERANK_BUDGET_EXCEEDED","RETRIEVAL_BUSY"))
            if(error.contains(code)) return code;
        String normalized=error.toLowerCase(Locale.ROOT);
        if(error.startsWith("SKILL_GROUPING_DEFERRED: JSONException")) return "SKILL_AUTHORING_MODEL_INVALID";
        if(normalized.contains("timeout") || normalized.contains("timed out")) return "BACKGROUND_MODEL_TIMEOUT";
        // Unknown diagnostics stay in the authorized audit, not a raw exception/credential on the page.
        return "BACKGROUND_FAILURE_RECORDED";
    }
}
