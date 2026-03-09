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
        return jdbcTemplate.queryForObject("""
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
    }

    @Override
    public SkillExperienceClusterEvidence clusterEvidence(
            String projectId,
            String agentId,
            String clusterKey) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT COUNT(DISTINCT o.run_id) AS distinct_runs,
                       COUNT(DISTINCT e.session_id) AS distinct_sessions,
                       SUM(CASE
                             WHEN o.outcome <> 'SUCCEEDED'
                               OR o.observation_type IN
                                  ('USER_NEGATIVE_FEEDBACK','ROUTING_CORRECTION','FAILED_THEN_RECOVERED_PATTERN')
                             THEN 1 ELSE 0 END) AS hard_case_count,
                       SUM(CASE WHEN o.outcome='SUCCEEDED' THEN 1 ELSE 0 END) AS successful_count,
                       GROUP_CONCAT(DISTINCT o.observation_type ORDER BY o.observation_type) AS observation_types
                FROM ai_ops_skill_observation o
                JOIN ai_ops_skill_run_episode e
                  ON e.episode_id=o.episode_id AND e.project_id=o.project_id
                WHERE o.project_id=? AND o.agent_id=? AND o.cluster_key=?
                """,
                projectId,
                agentId,
                clusterKey);
        if (rows.isEmpty()) return SkillExperienceClusterEvidence.empty();
        Map<String, Object> row = rows.get(0);
        String types = text(row.get("observation_types"));
        return new SkillExperienceClusterEvidence(
                integer(row.get("distinct_runs")),
                integer(row.get("distinct_sessions")),
                integer(row.get("hard_case_count")),
                integer(row.get("successful_count")),
                types.isBlank() ? List.of() : List.of(types.split(",")));
    }

    @Override
    public List<SkillExperienceConsolidationSample> consolidationSamples(
            String projectId,
            String agentId,
            String clusterKey,
            int limit) {
        return jdbcTemplate.query("""
                        SELECT o.observation_id,o.run_id,e.session_id,o.observation_type,o.outcome,
                               o.task_template_hash,o.trajectory_hash,o.content_json,
                               o.quality_score,o.evidence_refs_json
                        FROM ai_ops_skill_observation o
                        JOIN ai_ops_skill_run_episode e
                          ON e.episode_id=o.episode_id AND e.project_id=o.project_id
                        WHERE o.project_id=? AND o.agent_id=? AND o.cluster_key=?
                        ORDER BY o.quality_score DESC,o.create_time DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new SkillExperienceConsolidationSample(
                        text(rs.getString("observation_id")),
                        text(rs.getString("run_id")),
                        text(rs.getString("session_id")),
                        text(rs.getString("observation_type")),
                        text(rs.getString("outcome")),
                        text(rs.getString("task_template_hash")),
                        text(rs.getString("trajectory_hash")),
                        summary(rs.getString("content_json")),
                        rs.getDouble("quality_score"),
                        evidenceReferences(rs.getString("evidence_refs_json"))),
                projectId,
                agentId,
                clusterKey,
                Math.max(1, Math.min(limit, 32)));
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
