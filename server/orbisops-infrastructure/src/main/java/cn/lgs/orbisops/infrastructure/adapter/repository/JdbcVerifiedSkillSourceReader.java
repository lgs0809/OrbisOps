package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Counts current verified business tasks, joining late alert correlations before deduplication. */
final class JdbcVerifiedSkillSourceReader {
    private final JdbcTemplate jdbc;
    JdbcVerifiedSkillSourceReader(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    List<Map<String,Object>> sources(String project,String agent,String cluster) {
        return read(project,"(?='' OR c.agent_id=?) AND c.cluster_key=?",
                List.of(cluster.startsWith("eg-")?"":agent,agent,cluster),List.of());
    }

    /** One deduplication scope across method groups, with current proposal sources preferred. */
    List<Map<String,Object>> sourcesByIds(String project,List<String> sourceIds) {
        if(sourceIds.isEmpty()) return List.of();
        if(sourceIds.size()>cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy.ARCHIVE_LIMIT || new HashSet<>(sourceIds).size()!=sourceIds.size())
            throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_SET_INVALID");
        return read(project,"c.acceptance_id IN ("+String.join(",",Collections.nCopies(sourceIds.size(),"?"))+")",
                new ArrayList<>(sourceIds),sourceIds);
    }

    private List<Map<String,Object>> read(String project,String filter,List<?> parameters,List<String> priority) {
        var args=new ArrayList<Object>();args.add(project);args.addAll(parameters);
        var rows=jdbc.queryForList("""
                SELECT c.*,e.session_id,a.record_json,a.record_hash,i.incident_id,g.group_id
                FROM ai_ops_skill_verified_contribution c
                JOIN ai_ops_task_episode e ON e.episode_id=c.task_episode_id AND e.project_id=c.project_id
                    AND e.revision=c.task_revision AND e.outcome='SUCCEEDED' AND e.verified_outcome_ref=c.acceptance_id
                JOIN ai_ops_task_acceptance a ON a.acceptance_id=c.acceptance_id AND a.project_id=c.project_id
                    AND a.episode_id=e.episode_id AND a.episode_revision=e.revision AND a.outcome='SUCCEEDED'
                    AND a.condition_key=c.condition_key AND a.source_run_id=c.run_id
                LEFT JOIN ai_ops_task_episode_turn source ON source.episode_id=e.episode_id AND source.project_id=e.project_id
                    AND source.status='ASSIGNED' AND source.episode_revision<=e.revision
                LEFT JOIN ai_ops_incident_run link ON link.run_id=source.source_run_ref
                LEFT JOIN ai_ops_incident i ON i.incident_id=link.incident_id AND i.project_id=c.project_id
                LEFT JOIN ai_ops_alert_correlation_member m ON m.incident_id=i.incident_id
                LEFT JOIN ai_ops_alert_correlation_group g ON g.group_id=m.group_id AND g.project_id=c.project_id AND g.merged_into IS NULL
                WHERE c.project_id=? AND
                """+filter+"""
                  AND NOT EXISTS (SELECT 1 FROM ai_ops_chat_message msg LEFT JOIN ai_ops_task_episode_turn t
                    ON t.source_run_ref=msg.turn_id AND t.session_id=msg.session_id
                    WHERE msg.session_id=e.session_id AND msg.project_id=e.project_id AND msg.role='user' AND (t.id IS NULL OR t.status<>'ASSIGNED'))
                ORDER BY c.created_at DESC,c.observation_id DESC LIMIT 10001
                """,args.toArray());
        if (rows.size()>10000) throw new IllegalStateException("SKILL_VERIFIED_SOURCE_SCAN_LIMIT");
        Map<String,String> parents=new HashMap<>();
        List<Map<String,Object>> valid=new ArrayList<>();
        Map<String,Boolean> integrity = new HashMap<>();
        var verifier = new JdbcTaskAcceptanceIntegrity(jdbc);
        for(var r:rows) {
            if (!integrity.computeIfAbsent(text(r.get("acceptance_id")), id -> verifier.valid(project,
                    text(r.get("task_episode_id")),number(r.get("task_revision")),text(r.get("run_id")),id,
                    text(r.get("condition_key")),text(r.get("record_json")),text(r.get("record_hash"))))) continue;
            String task="episode:"+r.get("task_episode_id");root(parents,task);
            for(String field:List.of("incident_id","group_id")) {
                String value=text(r.get(field));if(!value.isBlank()) join(parents,task,field+":"+value);
            }
            valid.add(r);
        }
        if(!priority.isEmpty()) valid.sort(Comparator.comparingInt(r -> priority.indexOf(text(r.get("acceptance_id")))));
        Map<String,Map<String,Object>> independent=new LinkedHashMap<>();
        for(var r:valid) {
            String identity=root(parents,"episode:"+r.get("task_episode_id"));
            independent.putIfAbsent(identity,r);
        }
        return List.copyOf(independent.values());
    }
    private String root(Map<String,String> parents,String id) {
        parents.putIfAbsent(id,id);String root=id;
        while(!parents.get(root).equals(root)) root=parents.get(root);
        while(!id.equals(root)) {String next=parents.put(id,root);id=next;}
        return root;
    }
    private void join(Map<String,String> parents,String a,String b) {
        String left=root(parents,a),right=root(parents,b);if(!left.equals(right)) parents.put(right,left);
    }
}
