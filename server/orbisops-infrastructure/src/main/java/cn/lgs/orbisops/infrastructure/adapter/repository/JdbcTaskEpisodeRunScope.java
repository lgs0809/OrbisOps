package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.runtime.workflow.DurableWorkflowCheckpointCodec;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Evidence belongs to a task through committed workflow outputs, never a caller's parentRunId. */
final class JdbcTaskEpisodeRunScope {
    record Run(String runId,String rootRunId,long revision,int depth,Map<String,Object> row) { }
    private final JdbcTemplate jdbc;
    JdbcTaskEpisodeRunScope(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    List<Run> runs(String project,String episode) {
        var roots=jdbc.queryForList("""
                SELECT r.*,t.episode_revision FROM ai_ops_task_episode_turn t
                JOIN ai_ops_agent_run r ON r.run_id=t.source_run_ref AND r.project_id=t.project_id
                WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED' ORDER BY t.turn_seq LIMIT 201
                """,project,episode);
        var scope=new LinkedHashMap<String,Run>();
        for(var root:roots) {
            String id=text(root.get("run_id"));
            if(scope.putIfAbsent(id,new Run(id,id,number(root.get("episode_revision")),0,root))!=null) invalid();
        }
        var pending=new ArrayDeque<>(scope.values());
        while(!pending.isEmpty()) {
            if(scope.size()>200) throw new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_TOO_LARGE");
            var parent=pending.removeFirst();
            var checkpoints=jdbc.queryForList("""
                    SELECT checkpoint_json,checkpoint_hash FROM ai_ops_agent_run_checkpoint
                    WHERE run_id=? AND project_id=? AND checkpoint_type LIKE 'WORKFLOW_%'
                    ORDER BY checkpoint_seq DESC LIMIT 1
                    """,parent.runId(),project);
            if(checkpoints.isEmpty()) continue;
            var payload=CanonicalJson.parseObject(text(checkpoints.get(0).get("checkpoint_json")));
            if(!CanonicalObjectHasher.sha256(payload).equals(checkpoints.get(0).get("checkpoint_hash"))) invalid();
            var state=new DurableWorkflowCheckpointCodec().decode(payload).state();
            if(!project.equals(state.projectId()) || !parent.runId().equals(state.runId())
                    || !state.definitionHash().equals(parent.row().get("agent_definition_hash"))) invalid();
            for(var node:state.nodeStates().values()) {
                if(node.status()!=DurableWorkflowNodeStatus.SUCCEEDED) continue;
                Object value=state.variables().get("nodeOutput:"+node.nodeId());
                if(!(value instanceof Map<?,?> output) || !(output.get("childRunId") instanceof String childId)) continue;
                if(parent.depth()>=4 || !CanonicalObjectHasher.sha256(output).equals(node.outputHash())) invalid();
                String workflow=text(output.get("workflowId"));
                String suffix=CanonicalObjectHasher.sha256(Map.of("parentRunId",parent.runId(),"nodeId",node.nodeId(),"workflowId",workflow)).substring(0,16);
                String expected=parent.runId().substring(0,Math.min(parent.runId().length(),59))+"-sub-"+suffix;
                if(!expected.equals(childId)) invalid();
                var children=jdbc.queryForList("SELECT * FROM ai_ops_agent_run WHERE run_id=? AND project_id=?",childId,project);
                if(children.size()!=1) invalid();
                var child=children.get(0);var metadata=object(object(child.get("request_json")).get("metadata"));
                if(!parent.runId().equals(metadata.get("parentRunId")) || !node.nodeId().equals(metadata.get("parentNodeId"))
                        || !"SUB_WORKFLOW".equals(metadata.get("source")) || !workflow.equals(child.get("agent_id"))
                        || !Objects.equals(parent.row().get("user_id"),child.get("user_id"))
                        || !text(output.get("workflowVersion")).equals(text(child.get("agent_version")))
                        || !text(output.get("workflowDefinitionHash")).equals(text(child.get("agent_definition_hash")))
                        || !state.definitionHash().equals(metadata.get("parentWorkflowDefinitionHash"))) invalid();
                var found=new Run(childId,parent.rootRunId(),parent.revision(),parent.depth()+1,child);
                if(scope.putIfAbsent(childId,found)!=null) invalid();
                pending.addLast(found);
            }
        }
        return List.copyOf(scope.values());
    }

    List<Map<String,Object>> receipts(String project,String episode) {
        var runs=runs(project,episode);if(runs.isEmpty()) return List.of();
        var args=new ArrayList<Object>();args.add(project);runs.forEach(r->args.add(r.runId()));
        var rows=jdbc.queryForList("SELECT r.* FROM ai_ops_tool_result r WHERE r.project_id=? AND r.run_id IN ("
                +String.join(",",Collections.nCopies(runs.size(),"?"))+") ORDER BY r.id LIMIT 201",args.toArray());
        if(rows.size()>200) throw new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_TOO_LARGE");
        var byId=new HashMap<String,Run>();runs.forEach(r->byId.put(r.runId(),r));
        return rows.stream().map(row->{var result=new LinkedHashMap<>(row);var run=byId.get(text(row.get("run_id")));
            result.put("episode_revision",run.revision());result.put("root_run_id",run.rootRunId());return (Map<String,Object>)result;}).toList();
    }
    private static void invalid() { throw new SecurityException("TASK_ACCEPTANCE_CHILD_SCOPE_INVALID"); }
}
