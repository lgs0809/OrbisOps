package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** MySQL authority for private method facts, live groups and immutable grouping versions. */
@Repository
@Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
public class JdbcSkillExperienceGroupingStore implements SkillExperienceGroupingStore {
    private final JdbcTemplate jdbc;
    public JdbcSkillExperienceGroupingStore(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Override public List<cn.lgs.orbisops.domain.skill.model.SkillEvolutionRunCandidate> ungrouped(int limit) {
        return jdbc.query("""
            SELECT j.run_id,j.session_id,j.project_id,j.agent_id,r.status,r.updated_at
            FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state state ON state.job_id=j.job_id
            JOIN ai_ops_skill_evolution_source s ON s.source_id=state.source_id AND s.project_id=j.project_id
            JOIN ai_ops_task_episode e ON e.episode_id=s.episode_id AND e.project_id=s.project_id AND e.revision=s.episode_revision
              AND e.outcome='SUCCEEDED' AND e.verified_outcome_ref=s.source_id
            JOIN ai_ops_agent_run r ON r.run_id=j.run_id AND r.project_id=j.project_id
            WHERE (j.status='SKIPPED' OR (j.status='FAILED' AND j.last_error='Incorrect result size: expected 1, actual 0'))
              AND s.candidate_id='' AND NOT EXISTS
              (SELECT 1 FROM ai_ops_skill_method_experience f WHERE f.source_id=s.source_id)
              AND (SELECT p.decision FROM ai_ops_skill_evolution_patch p WHERE p.job_id=j.job_id ORDER BY p.id DESC LIMIT 1)
                IN ('SKIP_INSUFFICIENT_REPEATED_OBSERVATIONS','SKIP_INSUFFICIENT_SOURCE_DIVERSITY','SKIP_NO_REUSABLE_PATTERN')
            ORDER BY j.id LIMIT ?
            """,(r,n)->new cn.lgs.orbisops.domain.skill.model.SkillEvolutionRunCandidate(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6).toInstant()),Math.max(1,Math.min(limit,20)));
    }
    @Override public Optional<Fact> fact(SkillEvolutionJobSnapshot claim,String sourceHash) {
        requireSource(claim,sourceHash);
        return factRow(claim).map(row -> {
            var fact=fact(row);if(!sourceHash.equals(fact.sourceHash())) throw new IllegalStateException("SKILL_GROUPING_SOURCE_CHANGED");return fact;
        });
    }
    @Override public Fact saveFact(SkillEvolutionJobSnapshot claim,String sourceHash,Extraction extraction) {
        var source=requireSource(claim,sourceHash);
        String raw=CanonicalJson.stringify(extraction.method().view());
        jdbc.update("""
            INSERT IGNORE INTO ai_ops_skill_method_experience
            (source_id,project_id,run_id,episode_id,episode_revision,source_hash,method_json,method_hash,extraction_audit)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,claim.sourceId(),claim.projectId(),claim.runId(),source.get("episodeId"),number(source.get("revision")),
                sourceHash,raw,hash(raw),extraction.auditJson());
        return fact(factRow(claim).orElseThrow());
    }
    @Override public Optional<Group> assigned(SkillEvolutionJobSnapshot claim,String sourceHash) {
        requireSource(claim,sourceHash);
        return factRow(claim).filter(r -> "GROUPED".equals(r.get("status"))).flatMap(r -> current(claim.projectId(),text(r.get("group_id"))));
    }
    @Override public Optional<Group> current(String project,String groupId) {
        var rows=jdbc.queryForList("SELECT * FROM ai_ops_skill_method_group WHERE project_id=? AND group_id=? FOR UPDATE",project,groupId);
        if(rows.isEmpty()) return Optional.empty();
        var members=new JdbcVerifiedSkillSourceReader(jdbc).sources(project,"",groupId);
        var facts=new ArrayList<Fact>();
        for(var member:members) {
            var stored=jdbc.queryForList("SELECT * FROM ai_ops_skill_method_experience WHERE source_id=? AND project_id=? AND group_id=? AND status='GROUPED'",
                    member.get("acceptance_id"),project,groupId);
            if(stored.size()!=1) continue;
            var f=fact(stored.get(0));
            var live=new JdbcSkillEvolutionSourceReader(jdbc).loadAccepted(project,text(member.get("run_id")),f.sourceId());
            if(!live.sourceHash().equals(f.sourceHash())) throw new IllegalStateException("SKILL_GROUPING_SOURCE_CHANGED");
            facts.add(f);
        }
        if(facts.isEmpty()) return Optional.empty();
        facts.sort(Comparator.comparing(Fact::sourceId));
        var method=SkillMethodMemory.representative(facts);String sha=SkillMethodMemory.hash(method,facts);
        long version=number(rows.get(0).get("version"));
        if(!sha.equals(rows.get(0).get("content_hash"))) {
            version++;
            jdbc.update("UPDATE ai_ops_skill_method_group SET version=?,method_json=?,content_hash=?,status='ACTIVE' WHERE project_id=? AND group_id=?",
                    version,CanonicalJson.stringify(method.view()),sha,project,groupId);
            jdbc.update("INSERT INTO ai_ops_skill_method_group_version(group_id,version,content_hash,method_json,sources_json) VALUES(?,?,?,?,?)",
                    groupId,version,sha,CanonicalJson.stringify(method.view()),CanonicalJson.stringify(facts.stream().map(Fact::view).toList()));
        }
        return Optional.of(new Group(groupId,project,version,sha,method,facts));
    }
    @Override public Group commit(SkillEvolutionJobSnapshot claim,String sourceHash,SkillExperienceRecordResult observed,Decision decision,List<Group> compared) {
        requireSource(claim,sourceHash);
        var record=factRow(claim).orElseThrow();
        if("GROUPED".equals(record.get("status"))) return current(claim.projectId(),text(record.get("group_id"))).orElseThrow();
        if(!Set.of("CREATE","APPEND").contains(decision.action())) throw new IllegalArgumentException("SKILL_GROUPING_DECISION_INVALID");
        String groupId;
        if("APPEND".equals(decision.action())) {
            var expected=compared.stream().filter(g->g.groupId().equals(decision.groupId()) && g.projectId().equals(claim.projectId())).findFirst()
                    .orElseThrow(()->new SecurityException("SKILL_GROUPING_UNKNOWN_GROUP"));
            var live=current(claim.projectId(),expected.groupId()).orElseThrow(()->new IllegalStateException("SKILL_GROUPING_STALE_GROUP"));
            if(!live.reference().equals(expected.reference())) throw new IllegalStateException("SKILL_GROUPING_STALE_GROUP");
            groupId=live.groupId();
        } else {
            groupId="eg-"+hash(claim.projectId()+":"+claim.sourceId()).substring(0,61);
            jdbc.update("INSERT IGNORE INTO ai_ops_skill_method_group(group_id,project_id,method_json) VALUES(?,?,'{}')",groupId,claim.projectId());
        }
        if(!observed.observation().projectId().equals(claim.projectId()) || !observed.observation().runId().equals(claim.runId()))
            throw new SecurityException("SKILL_GROUPING_OBSERVATION_MISMATCH");
        jdbc.update("UPDATE ai_ops_skill_method_experience SET group_id=?,status='GROUPED',last_decision=? WHERE source_id=? AND project_id=?",
                groupId,decision.auditJson(),claim.sourceId(),claim.projectId());
        int contributions=jdbc.update("UPDATE ai_ops_skill_verified_contribution SET cluster_key=? WHERE acceptance_id=? AND project_id=? AND run_id=?",
                groupId,claim.sourceId(),claim.projectId(),claim.runId());
        if(contributions==0) throw new IllegalStateException("SKILL_GROUPING_CONTRIBUTION_MISSING");
        jdbc.update("UPDATE ai_ops_skill_observation SET cluster_key=? WHERE observation_id=? AND project_id=? AND run_id=?",
                groupId,observed.observation().observationId(),claim.projectId(),claim.runId());
        jdbc.update("""
            INSERT IGNORE INTO ai_ops_skill_experience_cluster(project_id,agent_id,cluster_key,latest_observation_id,status,version)
            VALUES(?,?,?,?,'ACCUMULATING',1)
            """,claim.projectId(),claim.agentId(),groupId,observed.observation().observationId());
        return current(claim.projectId(),groupId).orElseThrow();
    }
    @Override public void defer(SkillEvolutionJobSnapshot claim,String sourceHash,Decision decision) {
        requireSource(claim,sourceHash);
        jdbc.update("UPDATE ai_ops_skill_method_experience SET status='REVIEW',last_decision=? WHERE source_id=? AND project_id=? AND status<>'GROUPED'",
                decision.auditJson(),claim.sourceId(),claim.projectId());
    }
    private Map<String,Object> requireSource(SkillEvolutionJobSnapshot claim,String sha) {
        jdbc.queryForList("SELECT session_id FROM ai_ops_task_episode_session WHERE project_id=? AND session_id=? FOR UPDATE",claim.projectId(),claim.sessionId());
        jdbc.queryForList("SELECT e.episode_id FROM ai_ops_task_episode e JOIN ai_ops_task_episode_turn t ON t.episode_id=e.episode_id AND t.project_id=e.project_id WHERE t.project_id=? AND t.source_run_ref=? FOR UPDATE",claim.projectId(),claim.runId());
        var input=new JdbcSkillEvolutionSourceReader(jdbc).load(claim);
        if(!sha.equals(input.sourceHash()) || sha.isBlank()) throw new IllegalStateException("SKILL_GROUPING_SOURCE_CHANGED");
        return CanonicalJson.parseObject(input.episodeJson());
    }
    private Optional<Map<String,Object>> factRow(SkillEvolutionJobSnapshot claim) {
        return jdbc.queryForList("SELECT * FROM ai_ops_skill_method_experience WHERE source_id=? AND project_id=? FOR UPDATE",claim.sourceId(),claim.projectId()).stream().findFirst();
    }
    private Fact fact(Map<String,Object> row) {
        String raw=text(row.get("method_json"));
        if(!hash(raw).equals(row.get("method_hash"))) throw new IllegalStateException("SKILL_GROUPING_METHOD_CHANGED");
        return new Fact(text(row.get("source_id")),text(row.get("source_hash")),text(row.get("episode_id")),number(row.get("episode_revision")),
                Method.from(CanonicalJson.parseObject(raw)));
    }
}
