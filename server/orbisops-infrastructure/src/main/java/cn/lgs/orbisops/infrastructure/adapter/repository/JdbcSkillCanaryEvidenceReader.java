package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillCanaryEvidence;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.application.skill.SkillCanaryReviewPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Reconstructs exposure from immutable bundles, not only successfully recorded usage. */
final class JdbcSkillCanaryEvidenceReader {
    private final JdbcTemplate jdbc;
    JdbcSkillCanaryEvidenceReader(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    SkillCanaryEvidence read(SkillReleaseSnapshot release) {
        var rows=jdbc.queryForList("""
                SELECT b.id,b.run_id,b.used_skill_version_refs_json,b.used_skill_refs_hash,
                       t.episode_id,e.session_id,e.revision,e.outcome,e.verified_outcome_ref,
                       a.source_run_id,a.condition_key,a.record_json,a.record_hash,i.incident_id,g.group_id
                FROM ai_ops_runtime_context_bundle b
                JOIN JSON_TABLE(IF(JSON_VALID(b.used_skill_version_refs_json),b.used_skill_version_refs_json,'[]'),
                  '$[*]' COLUMNS(release_id VARCHAR(80) PATH '$.releaseId',skill_id VARCHAR(128) PATH '$.skillId',
                    skill_version INT PATH '$.version',skill_hash VARCHAR(128) PATH '$.skillHash',scope VARCHAR(24) PATH '$.scope')) refs
                    ON refs.release_id=? OR (? > 0 AND refs.skill_id=? AND refs.skill_version=? AND refs.skill_hash=? AND refs.scope='PROJECT')
                LEFT JOIN ai_ops_task_episode_turn t ON t.project_id=b.project_id AND t.source_run_ref=b.run_id AND t.status='ASSIGNED'
                LEFT JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=b.project_id
                LEFT JOIN ai_ops_task_acceptance a ON a.acceptance_id=e.verified_outcome_ref AND a.project_id=e.project_id
                    AND a.episode_id=e.episode_id AND a.episode_revision=e.revision
                LEFT JOIN ai_ops_task_episode_turn related ON related.project_id=e.project_id AND related.episode_id=e.episode_id AND related.status='ASSIGNED'
                LEFT JOIN ai_ops_incident_run link ON link.run_id=related.source_run_ref
                LEFT JOIN ai_ops_incident i ON i.incident_id=link.incident_id AND i.project_id=b.project_id
                LEFT JOIN ai_ops_alert_correlation_member m ON m.incident_id=i.incident_id
                LEFT JOIN ai_ops_alert_correlation_group g ON g.group_id=m.group_id AND g.project_id=b.project_id AND g.merged_into IS NULL
                WHERE b.project_id=? ORDER BY b.id LIMIT 10001
                """,release.releaseId(),release.releasedVersion(),release.targetSkillId(),release.releasedVersion(),release.releasedSkillHash(),release.projectId());
        if(rows.size()>10000) {
            int safety=(int)new JdbcSkillCanaryReviewStore(jdbc).findings(release.projectId(),release.releaseId()).stream()
                    .filter(f -> "VIOLATION".equals(((SkillCanaryReviewPort.Decision)f.get("decision")).safety())).count();
            return new SkillCanaryEvidence(0,0,0,1,0,safety,0,0);
        }
        var parents=new HashMap<String,String>();
        for(var row:rows) {
            String episode=text(row.get("episode_id")); if(episode.isBlank()) continue;
            root(parents,episode);
            for(String key:List.of("incident_id","group_id")) {
                String value=text(row.get(key));
                if(!value.isBlank()) parents.put(root(parents,key+":"+value),root(parents,episode));
            }
        }
        var tasks=new LinkedHashMap<String,LinkedHashMap<String,Map<String,Object>>>();
        int unresolved=0;
        var inspectedBundles=new HashSet<Object>();
        var contracts=new JdbcSkillCanaryContractReader(jdbc);
        for(var row:rows) {
            if(!inspectedBundles.add(row.get("id"))) continue;
            String raw=text(row.get("used_skill_version_refs_json"));
            if (!hash(CanonicalJson.stringify(CanonicalJson.parse(raw))).equals(text(row.get("used_skill_refs_hash")))) {
                unresolved++; continue;
            }
            String episode=text(row.get("episode_id"));
            if(episode.isBlank()) { if(tasks.size()<20) unresolved++; continue; }
            String identity=root(parents,episode);
            if(tasks.size()<20 || tasks.containsKey(identity)) {
                tasks.computeIfAbsent(identity,unused->new LinkedHashMap<>()).putIfAbsent(episode,row);
                if(!contracts.matches(release,text(row.get("run_id"))) || !contracts.candidateMatches(release,raw)) unresolved++;
            }
        }
        int resolved=0,success=0,reviewed=0; var conditions=new HashSet<String>();
        var reviews=new JdbcSkillCanaryReviewStore(jdbc);
        for(var task:tasks.values()) {
          boolean allResolved=true,allSucceeded=true,allReviewed=true;
          var taskConditions=new HashSet<String>();
          for(var row:task.values()) {
            String run=text(row.get("source_run_id"));
            var verified=new JdbcVerifiedTaskOutcomeReader(jdbc).read(release.projectId(),run);
            if(verified.matches(release.projectId(),run)) {
                taskConditions.add(verified.conditionKey());
            } else if("FAILED".equals(text(row.get("outcome")))
                    && new JdbcVerifiedTaskOutcomeReader(jdbc).ready(release.projectId(),text(row.get("episode_id")),text(row.get("session_id")))
                    && new JdbcTaskAcceptanceIntegrity(jdbc).validOutcome(release.projectId(),text(row.get("episode_id")),number(row.get("revision")),run,
                        text(row.get("verified_outcome_ref")),text(row.get("condition_key")),text(row.get("record_json")),text(row.get("record_hash")),"FAILED")) {
                // A failure stays in the denominator. It is not automatically attributed to the candidate.
                allSucceeded=false;
            } else {allResolved=false;allSucceeded=false;}
            var review=reviews.current(release.projectId(),text(row.get("episode_id")),release.releaseId());
            allReviewed &= review.isPresent() && review.get().conclusive();
          }
          if(allResolved) resolved++;
          if(allSucceeded) {success++;conditions.addAll(taskConditions);}
          if(allReviewed) reviewed++;
        }
        // A blocked request is not evidence that an unauthorized side effect actually happened.
        // Explicit, independently verified safety/attribution findings are evaluated separately.
        var unsafe=new HashSet<String>();var attributed=new HashSet<String>();
        for(var finding:reviews.findings(release.projectId(),release.releaseId())) {
            String identity=root(parents,text(finding.get("episodeId")));
            var decision=(SkillCanaryReviewPort.Decision)finding.get("decision");
            if("VIOLATION".equals(decision.safety())) unsafe.add(identity);
            if(tasks.containsKey(identity) && "CANDIDATE".equals(decision.attribution())) attributed.add(identity);
        }
        var requiredConditions=contracts.conditions(release);
        if(!tasks.isEmpty() && (requiredConditions.isEmpty() || !conditions.containsAll(requiredConditions))) unresolved++;
        return new SkillCanaryEvidence(tasks.size(),resolved,success,unresolved,conditions.size(),unsafe.size(),attributed.size(),reviewed);
    }
    private String root(Map<String,String> parents,String id) {
        parents.putIfAbsent(id,id);String result=id;
        while(!parents.get(result).equals(result)) result=parents.get(result);
        while(!id.equals(result)) {String next=parents.put(id,result);id=next;}
        return result;
    }
}
