package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillExperiencePort;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterEvidence;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JDBC implementation for Skill experience Episodes, Observations and Clusters. */
@Repository
public class JdbcSkillExperienceAdapter implements SkillExperiencePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillExperienceAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertEpisode(SkillExperienceObservation observation) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_run_episode
                  (episode_id,project_id,agent_id,run_id,session_id,task_template_json,task_template_hash,
                   abstract_trajectory_json,trajectory_hash,outcome,evidence_refs_json,final_summary,event_count,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'COMPLETED')
                ON DUPLICATE KEY UPDATE
                  agent_id=VALUES(agent_id),session_id=VALUES(session_id),task_template_json=VALUES(task_template_json),
                  task_template_hash=VALUES(task_template_hash),abstract_trajectory_json=VALUES(abstract_trajectory_json),
                  trajectory_hash=VALUES(trajectory_hash),outcome=VALUES(outcome),evidence_refs_json=VALUES(evidence_refs_json),
                  final_summary=VALUES(final_summary),event_count=VALUES(event_count),status='COMPLETED',
                  update_time=CURRENT_TIMESTAMP(3)
                """,
                observation.episodeId(),
                observation.projectId(),
                observation.agentId(),
                observation.runId(),
                observation.sessionId(),
                JSON.toJSONString(taskTemplate(observation)),
                observation.taskTemplateHash(),
                JSON.toJSONString(observation.abstractTrajectory()),
                observation.trajectoryHash(),
                observation.outcome(),
                JSON.toJSONString(evidenceViews(observation)),
                observation.finalSummary(),
                observation.eventCount());
    }

    @Override
    public boolean insertObservation(SkillExperienceObservation observation) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO ai_ops_skill_observation
                  (observation_id,episode_id,project_id,agent_id,run_id,observation_type,cluster_key,
                   task_template_hash,trajectory_hash,content_json,evidence_refs_json,outcome,quality_score,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'OBSERVED')
                """,
                observation.observationId(),
                observation.episodeId(),
                observation.projectId(),
                observation.agentId(),
                observation.runId(),
                observation.observationType(),
                observation.clusterKey(),
                observation.taskTemplateHash(),
                observation.trajectoryHash(),
                JSON.toJSONString(Map.of(
                        "taskTemplate",
                        taskTemplate(observation),
                        "abstractTrajectory",
                        observation.abstractTrajectory(),
                        "summary",
                        observation.finalSummary())),
                JSON.toJSONString(evidenceViews(observation)),
                observation.outcome(),
                observation.qualityScore()) == 1;
    }

    @Override public boolean recordVerifiedContribution(SkillExperienceObservation observation) {
        var v=observation.verifiedTaskOutcome();
        if (!v.matches(observation.projectId(),observation.runId()) || !"SUCCEEDED".equals(observation.outcome())) return false;
        boolean inserted = jdbcTemplate.update("""
                INSERT IGNORE INTO ai_ops_skill_verified_contribution
                (observation_id,project_id,agent_id,cluster_key,run_id,task_episode_id,task_revision,acceptance_id,condition_key,
                 observation_type,content_json,evidence_refs_json,task_template_hash,trajectory_hash,quality_score)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,observation.observationId(),observation.projectId(),observation.agentId(),observation.clusterKey(),observation.runId(),
                v.taskEpisodeId(),v.revision(),v.verificationId(),v.conditionKey(),observation.observationType(),
                JSON.toJSONString(Map.of("taskTemplate",taskTemplate(observation),"abstractTrajectory",observation.abstractTrajectory(),"summary",observation.finalSummary())),
                JSON.toJSONString(evidenceViews(observation)),observation.taskTemplateHash(),observation.trajectoryHash(),observation.qualityScore()) == 1;
        if (!inserted) jdbcTemplate.update("""
                UPDATE ai_ops_skill_verified_contribution SET task_revision=?,acceptance_id=?,condition_key=?
                WHERE observation_id=? AND project_id=? AND run_id=? AND task_episode_id=?
                """,v.revision(),v.verificationId(),v.conditionKey(),observation.observationId(),observation.projectId(),observation.runId(),v.taskEpisodeId());
        return inserted;
    }

    @Override public java.util.Optional<String> recordedClusterKey(String projectId,String agentId,String observationId) {
        return jdbcTemplate.query("SELECT cluster_key FROM ai_ops_skill_observation WHERE project_id=? AND agent_id=? AND observation_id=?",
                (r,n)->r.getString(1),projectId,agentId,observationId).stream().findFirst();
    }

    @Override
    public void upsertCluster(SkillExperienceObservation observation) {
        jdbcTemplate.update("""
                INSERT INTO ai_ops_skill_experience_cluster
                  (project_id,agent_id,cluster_key,observation_count,successful_count,evidence_count,
                   latest_observation_id,status,version)
                VALUES (?,?,?,1,?,?,?,'ACCUMULATING',1)
                ON DUPLICATE KEY UPDATE
                  observation_count=observation_count+1,
                  successful_count=successful_count+VALUES(successful_count),
                  evidence_count=evidence_count+VALUES(evidence_count),
                  latest_observation_id=VALUES(latest_observation_id),version=version+1,
                  update_time=CURRENT_TIMESTAMP(3)
                """,
                observation.projectId(),
                observation.agentId(),
                observation.clusterKey(),
                "SUCCEEDED".equals(observation.outcome()) ? 1 : 0,
                observation.evidenceReferences().isEmpty() ? 0 : 1,
                observation.observationId());
    }

    @Override
    public SkillExperienceClusterSnapshot cluster(
            String projectId,
            String agentId,
            String clusterKey) {
        var snapshot = jdbcTemplate.queryForObject("""
                        SELECT observation_count,successful_count,evidence_count,status,version
                        FROM ai_ops_skill_experience_cluster
                        WHERE project_id=? AND agent_id=? AND cluster_key=?
                        """,
                (rs, rowNum) -> new SkillExperienceClusterSnapshot(
                        rs.getInt("observation_count"),
                        rs.getInt("successful_count"),
                        rs.getInt("evidence_count"),
                        text(rs.getString("status")),
                        rs.getInt("version")),
                projectId,
                agentId,
                clusterKey);
        int verified=new JdbcVerifiedSkillSourceReader(jdbcTemplate).sources(projectId,agentId,clusterKey).size();
        return new SkillExperienceClusterSnapshot(snapshot.observationCount(),verified,verified,snapshot.status(),snapshot.version());
    }

    @Override
    public SkillExperienceClusterEvidence clusterEvidence(
            String projectId,
            String agentId,
            String clusterKey) {
        var sources=new JdbcVerifiedSkillSourceReader(jdbcTemplate).sources(projectId,agentId,clusterKey);
        return new SkillExperienceClusterEvidence(
                sources.size(),(int)sources.stream().map(s->s.get("session_id")).distinct().count(),
                (int)sources.stream().filter(s->List.of("USER_NEGATIVE_FEEDBACK","ROUTING_CORRECTION","FAILED_THEN_RECOVERED_PATTERN").contains(s.get("observation_type"))).count(),
                sources.size(),sources.stream().map(s->text(s.get("observation_type"))).distinct().sorted().toList(),
                (int)sources.stream().map(s->s.get("condition_key")).distinct().count());
    }

    @Override
    public List<SkillExperienceConsolidationSample> consolidationSamples(
            String projectId,
            String agentId,
            String clusterKey,
            int limit) {
        return consolidationSamples(projectId,agentId,clusterKey,limit,"");
    }

    @Override public List<SkillExperienceConsolidationSample> consolidationSamples(String projectId,String agentId,String clusterKey,int limit,String requiredRun) {
        var sourceReader=new JdbcSkillEvolutionSourceReader(jdbcTemplate);
        var metadata=new JdbcVerifiedSkillSourceReader(jdbcTemplate).sources(projectId,agentId,clusterKey);
        int budget=Math.max(1,Math.min(limit,cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy.ARCHIVE_LIMIT));
        if(metadata.size()>budget && budget>20) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_ARCHIVE_BUDGET");
        var selected=new LinkedHashMap<String,Map<String,Object>>();
        if(!requiredRun.isBlank()) {
            var current=metadata.stream().filter(r->requiredRun.equals(text(r.get("run_id")))).findFirst()
                    .orElseThrow(()->new IllegalStateException("SKILL_EVOLUTION_SOURCE_SET_INVALID"));
            selected.put(text(current.get("acceptance_id")),current);
        }
        var conditions=new java.util.HashSet<String>();selected.values().forEach(r->conditions.add(text(r.get("condition_key"))));
        for(var row:metadata) if(selected.size()<budget && conditions.add(text(row.get("condition_key"))))
            selected.put(text(row.get("acceptance_id")),row);
        for(var row:metadata) if(selected.size()<budget) selected.putIfAbsent(text(row.get("acceptance_id")),row);
        return selected.values().stream().map(r->{
                    var full=sourceReader.loadAccepted(projectId,text(r.get("run_id")),text(r.get("acceptance_id")));
                    return new SkillExperienceConsolidationSample(
                        text(r.get("observation_id")),text(r.get("run_id")),text(r.get("session_id")),text(r.get("observation_type")),"SUCCEEDED",
                        text(r.get("task_template_hash")),text(r.get("trajectory_hash")),summary(text(r.get("content_json"))),
                        ((Number)r.get("quality_score")).doubleValue(),evidenceReferences(text(r.get("evidence_refs_json"))),
                        text(r.get("acceptance_id")),full.sourceHash(),full.episodeJson(),text(r.get("task_episode_id")),text(r.get("condition_key")));
                }).toList();
    }

    @Override
    public boolean markClusterPromoted(
            String projectId,
            String agentId,
            String clusterKey,
            String candidateId) {
        return jdbcTemplate.update("""
                        UPDATE ai_ops_skill_experience_cluster
                        SET status='CANDIDATE_CREATED',candidate_id=?,update_time=CURRENT_TIMESTAMP(3)
                        WHERE project_id=? AND agent_id=? AND cluster_key=? AND status='ACCUMULATING'
                        """,
                candidateId,
                projectId,
                agentId,
                clusterKey) == 1;
    }

    @Override
    public void markObservationsPromoted(
            String projectId,
            String agentId,
            String clusterKey) {
        jdbcTemplate.update("""
                        UPDATE ai_ops_skill_observation
                        SET status='PROMOTED'
                        WHERE project_id=? AND agent_id=? AND cluster_key=? AND status='OBSERVED'
                        """,
                projectId,
                agentId,
                clusterKey);
    }

    private java.util.List<Map<String, Object>> evidenceViews(
            SkillExperienceObservation observation) {
        return observation.evidenceReferences().stream()
                .map(reference -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    putIfPresent(view, "evidenceId", reference.evidenceId());
                    putIfPresent(view, "resultId", reference.resultId());
                    putIfPresent(view, "outputHash", reference.outputHash());
                    putIfPresent(view, "sourceType", reference.sourceType());
                    return view;
                })
                .toList();
    }

    private void putIfPresent(
            Map<String, Object> target,
            String key,
            String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private Map<String, Object> taskTemplate(
            SkillExperienceObservation observation) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("intent", observation.taskTemplate().intent());
        view.put("problemPattern", observation.taskTemplate().problemPattern());
        view.put("triggerType", observation.taskTemplate().triggerType());
        view.put("evidenceTypes", observation.taskTemplate().evidenceTypes());
        view.put("outcome", observation.taskTemplate().outcome());
        return view;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String summary(String contentJson) {
        try {
            Map<?, ?> content = JSON.parseObject(contentJson, Map.class);
            return text(content == null ? null : content.get("summary"));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private List<SkillExperienceEvidenceReference> evidenceReferences(
            String evidenceJson) {
        List<SkillExperienceEvidenceReference> result = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(evidenceJson);
            if (array == null) return List.of();
            for (Object item : array) {
                if (!(item instanceof Map<?, ?> map)) continue;
                result.add(new SkillExperienceEvidenceReference(
                        text(map.get("evidenceId")),
                        text(map.get("resultId")),
                        text(map.get("outputHash")),
                        text(map.get("sourceType"))));
            }
        } catch (RuntimeException ignored) {
            return List.of();
        }
        return List.copyOf(result);
    }
}
