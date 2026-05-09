package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

@Repository
public class JdbcSkillCanaryReviewStore implements SkillCanaryReviewPort {
    private final JdbcTemplate jdbc;
    private int cursor;
    public JdbcSkillCanaryReviewStore(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public synchronized int discover(int limit) {
        var targets=jdbc.queryForList("""
                SELECT DISTINCT b.project_id,r.release_id,t.episode_id
                FROM ai_ops_runtime_context_bundle b
                JOIN JSON_TABLE(IF(JSON_VALID(b.used_skill_version_refs_json),b.used_skill_version_refs_json,'[]'),
                    '$[*]' COLUMNS(release_id VARCHAR(80) PATH '$.releaseId',skill_id VARCHAR(128) PATH '$.skillId',
                      skill_version INT PATH '$.version',skill_hash VARCHAR(128) PATH '$.skillHash',scope VARCHAR(24) PATH '$.scope')) refs
                JOIN ai_ops_skill_release r ON r.project_id=b.project_id AND r.status IN ('CANARY','ACTIVE')
                  AND (r.release_id=refs.release_id OR (r.status='ACTIVE' AND r.released_version>0 AND r.target_skill_id=refs.skill_id
                    AND r.released_version=refs.skill_version AND r.released_skill_hash=refs.skill_hash AND refs.scope='PROJECT'))
                JOIN ai_ops_task_episode_turn t ON t.source_run_ref=b.run_id AND t.project_id=b.project_id AND t.status='ASSIGNED'
                ORDER BY b.project_id,r.release_id,t.episode_id LIMIT 10001
                """);
        if(targets.size()>10000) throw new IllegalStateException("CANARY_REVIEW_DISCOVERY_LIMIT");
        int added=0;
        for(int i=0;i<Math.min(Math.max(0,limit),targets.size());i++) {
            var target=targets.get(Math.floorMod(cursor++,targets.size()));
            String project=text(target.get("project_id")),episode=text(target.get("episode_id")),release=text(target.get("release_id"));
            var input=new JdbcSkillCanaryReviewInput(jdbc).read(project,episode,release);
            if(input.isEmpty()) continue;
            String digest=hash(input.get());
            added+=jdbc.update("""
                    INSERT IGNORE INTO ai_ops_skill_canary_review(review_id,project_id,release_id,episode_id,input_hash,input_json)
                    VALUES (?,?,?,?,?,?)
                    """,digest,project,release,episode,digest,input.get());
        }
        return added;
    }
    @Override public Optional<Claim> claim(long now) {
        jdbc.update("UPDATE ai_ops_skill_canary_review SET status='EXHAUSTED',lease_token='' WHERE status='RUNNING' AND lease_until<=? AND attempt_count>=5",now);
        var rows=jdbc.queryForList("""
                SELECT review_id,input_json,input_hash,attempt_count FROM ai_ops_skill_canary_review
                WHERE attempt_count<5 AND ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=?) OR (status='RUNNING' AND lease_until<=?))
                ORDER BY next_attempt_at,created_at,review_id LIMIT 8
                """,now,now);
        for(var row:rows) {
            String id=text(row.get("review_id")),lease=UUID.randomUUID().toString();
            if(!id.equals(text(row.get("input_hash"))) || !id.equals(hash(text(row.get("input_json"))))) {
                jdbc.update("UPDATE ai_ops_skill_canary_review SET status='INVALID',reason_code='INPUT_HASH_MISMATCH' WHERE review_id=?",id);
                continue;
            }
            int attempt=(int)number(row.get("attempt_count"));
            if(jdbc.update("""
                    UPDATE ai_ops_skill_canary_review SET status='RUNNING',lease_token=?,lease_until=?,attempt_count=attempt_count+1
                    WHERE review_id=? AND attempt_count=? AND ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_at<=?) OR (status='RUNNING' AND lease_until<=?))
                    """,lease,now+30_000,id,attempt,now,now)==1)
                return Optional.of(new Claim(id,lease,text(row.get("input_json")),attempt+1));
        }
        return Optional.empty();
    }
    @Override public void complete(Claim claim, Decision decision) {
        if(decision==null) throw new IllegalArgumentException("CANARY_REVIEW_EMPTY");
        var input=CanonicalJson.parseObject(claim.input());
        if(!(input.get("evidenceIds") instanceof List<?> ids) || !ids.containsAll(decision.evidenceIds()))
            throw new IllegalArgumentException("CANARY_REVIEW_EVIDENCE_NOT_IN_SNAPSHOT");
        String result=CanonicalJson.stringify(Map.of("safety",decision.safety(),"attribution",decision.attribution(),
                "evidenceIds",decision.evidenceIds(),"reason",decision.reason(),"inputHash",claim.id(),
                "reviewerModel",SkillCanaryReviewModelPort.MODEL,"reviewerVersion",SkillCanaryReviewModelPort.VERSION));
        jdbc.update("""
                UPDATE ai_ops_skill_canary_review SET status='COMPLETED',safety=?,attribution=?,result_json=?,result_hash=?,
                    reviewer_model=?,reviewer_version=?,lease_token='',lease_until=0
                WHERE review_id=? AND status='RUNNING' AND lease_token=? AND input_hash=?
                """,decision.safety(),decision.attribution(),result,hash(result),SkillCanaryReviewModelPort.MODEL,
                SkillCanaryReviewModelPort.VERSION,claim.id(),claim.lease(),hash(claim.input()));
    }
    @Override public void retry(Claim claim,long dueAt,String reason) {
        jdbc.update("""
                UPDATE ai_ops_skill_canary_review SET status=IF(attempt_count>=5,'EXHAUSTED','RETRY_WAIT'),
                    next_attempt_at=?,reason_code=?,lease_token='',lease_until=0
                WHERE review_id=? AND status='RUNNING' AND lease_token=?
                """,dueAt,reason,claim.id(),claim.lease());
    }
    Optional<Decision> current(String project,String episode,String release) {
        var input=new JdbcSkillCanaryReviewInput(jdbc).read(project,episode,release);
        if(input.isEmpty()) return Optional.empty();
        return jdbc.queryForList("SELECT * FROM ai_ops_skill_canary_review WHERE review_id=? AND project_id=? AND episode_id=? AND release_id=?",
                hash(input.get()),project,episode,release).stream().map(this::verified).flatMap(Optional::stream).findFirst();
    }
    List<Map<String,Object>> findings(String project,String release) {
        var results=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT * FROM ai_ops_skill_canary_review WHERE project_id=? AND release_id=? AND status='COMPLETED' AND (safety='VIOLATION' OR attribution='CANDIDATE')",project,release)) {
            var finding=verified(row);
            if(finding.isPresent()) results.add(Map.of("episodeId",text(row.get("episode_id")),"decision",finding.get()));
        }
        return results;
    }
    private Optional<Decision> verified(Map<String,Object> row) {
        try {
            if(!"COMPLETED".equals(row.get("status")) || !SkillCanaryReviewModelPort.MODEL.equals(row.get("reviewer_model"))
                    || !SkillCanaryReviewModelPort.VERSION.equals(row.get("reviewer_version"))) return Optional.empty();
            String raw=text(row.get("result_json")),input=text(row.get("input_json")),id=text(row.get("review_id"));
            if(!hash(raw).equals(row.get("result_hash")) || !id.equals(hash(input)) || !id.equals(row.get("input_hash"))) return Optional.empty();
            var result=CanonicalJson.parseObject(raw);var snapshot=CanonicalJson.parseObject(input);
            if(!id.equals(result.get("inputHash")) || !row.get("safety").equals(result.get("safety"))
                    || !row.get("attribution").equals(result.get("attribution"))
                    || !SkillCanaryReviewModelPort.MODEL.equals(result.get("reviewerModel"))
                    || !SkillCanaryReviewModelPort.VERSION.equals(result.get("reviewerVersion"))
                    || !row.get("project_id").equals(snapshot.get("projectId")) || !row.get("release_id").equals(snapshot.get("releaseId"))
                    || !(snapshot.get("episode") instanceof Map<?,?> episode) || !row.get("episode_id").equals(episode.get("episode_id"))
                    || !(result.get("evidenceIds") instanceof List<?> refs) || !(snapshot.get("evidenceIds") instanceof List<?> ids)
                    || !ids.containsAll(refs) || refs.stream().anyMatch(ref -> !(ref instanceof String))) return Optional.empty();
            return Optional.of(new Decision(text(result.get("safety")),text(result.get("attribution")),refs.stream().map(Object::toString).toList(),text(result.get("reason"))));
        } catch(IllegalArgumentException invalid) { return Optional.empty(); }
    }
}
