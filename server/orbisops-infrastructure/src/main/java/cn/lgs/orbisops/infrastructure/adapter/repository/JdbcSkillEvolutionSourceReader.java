package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillEvolutionSourcePort;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Whole accepted task input. Other goals in the same chat and unassigned turns never enter authoring. */
@Repository
public class JdbcSkillEvolutionSourceReader implements SkillEvolutionSourcePort {
    private final JdbcTemplate jdbc;
    public JdbcSkillEvolutionSourceReader(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc=jdbc; }

    // Called under the session/episode lock by the existing job repository.
    String freeze(SkillEvolutionJobSnapshot job) {
        var outcome = new JdbcVerifiedTaskOutcomeReader(jdbc).read(job.projectId(),job.runId());
        if (!outcome.matches(job.projectId(),job.runId())) return "";
        String sourceId=outcome.verificationId();
        if (!jdbc.queryForList("SELECT source_id FROM ai_ops_skill_evolution_source WHERE source_id=?",sourceId).isEmpty()) {
            stored(job,sourceId); return sourceId;
        }
        var episode=jdbc.queryForMap("SELECT goal FROM ai_ops_task_episode WHERE project_id=? AND episode_id=?",job.projectId(),outcome.taskEpisodeId());
        var turns=jdbc.queryForList("""
                SELECT t.source_run_ref,t.turn_seq,t.end_seq,t.episode_revision,t.input_hash,t.context_fidelity,
                       r.status,r.project_id AS run_project,r.session_id AS run_session
                FROM ai_ops_task_episode_turn t LEFT JOIN ai_ops_agent_run r ON r.run_id=t.source_run_ref
                WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED' ORDER BY t.turn_seq LIMIT 201
                """,job.projectId(),outcome.taskEpisodeId());
        bounded(turns,200);
        if (turns.isEmpty()) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_TURNS_MISSING");
        for (var turn:turns) {
            if (!job.projectId().equals(turn.get("run_project")) || !job.sessionId().equals(turn.get("run_session"))
                    || !Set.of("SUCCEEDED","FAILED","CANCELED").contains(text(turn.get("status")))
                    || number(turn.get("episode_revision"))>outcome.revision())
                throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_RUN_UNRESOLVED");
        }
        var messages=jdbc.queryForList("""
                SELECT m.turn_id AS runId,m.message_seq AS messageSeq,m.role,m.content
                FROM ai_ops_chat_message m JOIN ai_ops_task_episode_turn t
                  ON t.project_id=m.project_id AND t.session_id=m.session_id AND t.source_run_ref=m.turn_id
                WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED'
                ORDER BY t.turn_seq,m.message_seq,m.id LIMIT 401
                """,job.projectId(),outcome.taskEpisodeId());
        bounded(messages,400);
        var trace=jdbc.queryForList("""
                SELECT t.source_run_ref AS runId,n.event_type AS eventType,n.status,n.summary,n.payload_json AS payloadJson
                FROM ai_ops_agent_node_trace n JOIN ai_ops_task_episode_turn t ON t.source_run_ref=n.run_id
                WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED'
                ORDER BY t.turn_seq,n.sequence_no,n.id LIMIT 2001
                """,job.projectId(),outcome.taskEpisodeId());
        bounded(trace,2000);
        var scope=new JdbcTaskEpisodeRunScope(jdbc);
        var scopedRuns=scope.runs(job.projectId(),outcome.taskEpisodeId());
        var runScope=evidenceRunScope(scopedRuns);
        var childMessages=new ArrayList<Map<String,Object>>();
        var childTrace=new ArrayList<Map<String,Object>>();
        for(var child:scopedRuns) if(child.depth()>0) {
            childMessages.addAll(jdbc.queryForList("SELECT turn_id AS runId,message_seq AS messageSeq,role,content FROM ai_ops_chat_message WHERE project_id=? AND session_id=? AND turn_id=? ORDER BY message_seq,id LIMIT 401",
                    job.projectId(),child.row().get("session_id"),child.runId()));
            childTrace.addAll(jdbc.queryForList("SELECT run_id AS runId,event_type AS eventType,status,summary,payload_json AS payloadJson FROM ai_ops_agent_node_trace WHERE run_id=? ORDER BY sequence_no,id LIMIT 2001",child.runId()));
        }
        if(messages.size()+childMessages.size()>400 || trace.size()+childTrace.size()>2000)
            throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_TOO_LARGE");
        var receipts=sourceReceipts(scope.receipts(job.projectId(),outcome.taskEpisodeId()));
        bounded(receipts,200);
        for (var receipt:receipts) {
            if (!text(receipt.get("outputHash")).equals(hash(text(receipt.get("fullOutput")))))
                throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_RECEIPT_CHANGED");
        }
        var acceptance=jdbc.queryForMap("SELECT record_json,record_hash FROM ai_ops_task_acceptance WHERE acceptance_id=?",sourceId);
        Map<String,Object> source=new LinkedHashMap<>();
        source.put("format","accepted-task-episode-v1"); source.put("sourceId",sourceId);
        source.put("projectId",job.projectId()); source.put("sessionId",job.sessionId()); source.put("runId",job.runId());
        source.put("episodeId",outcome.taskEpisodeId()); source.put("revision",outcome.revision()); source.put("goal",episode.get("goal"));
        source.put("turns",turns); source.put("messages",messages); source.put("trace",trace); source.put("receipts",receipts);
        source.put("evidenceRunScope",runScope);
        source.put("childMessages",childMessages);source.put("childTrace",childTrace);
        source.put("acceptance",object(acceptance.get("record_json"))); source.put("acceptanceHash",acceptance.get("record_hash"));
        String raw=CanonicalJson.stringify(source);
        // Archival evidence has a separate byte limit from the model request. No truncation.
        if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16_000_000)
            throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_TOO_LARGE");
        jdbc.update("""
                INSERT INTO ai_ops_skill_evolution_source(source_id,project_id,session_id,run_id,episode_id,episode_revision,input_json,input_hash)
                VALUES (?,?,?,?,?,?,?,?)
                """,sourceId,job.projectId(),job.sessionId(),job.runId(),outcome.taskEpisodeId(),outcome.revision(),raw,hash(raw));
        return sourceId;
    }

    @Override public SkillEvolutionInput load(SkillEvolutionJobSnapshot job) {
        requireClaim(job);
        if (job.sourceId().isBlank()) return new SkillEvolutionInput(List.of(),List.of());
        return acceptedInput(job,job.sourceId());
    }

    /** Read another already frozen, still accepted source; this never creates or claims another job. */
    SkillEvolutionInput loadAccepted(String project,String run,String sourceId) {
        var rows=jdbc.queryForList("SELECT session_id,agent_id FROM ai_ops_agent_run WHERE run_id=? AND project_id=?",run,project);
        if(rows.size()!=1) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_RUN_UNRESOLVED");
        var row=rows.get(0);
        return acceptedInput(new SkillEvolutionJobSnapshot(0,"",run,text(row.get("session_id")),project,text(row.get("agent_id")),
                "",SkillEvolutionJobStatus.PENDING,0,null,"",null,null),sourceId);
    }

    private SkillEvolutionInput acceptedInput(SkillEvolutionJobSnapshot job,String sourceId) {
        var source=stored(job,sourceId);
        var outcome=new JdbcVerifiedTaskOutcomeReader(jdbc).read(job.projectId(),job.runId());
        if (!outcome.matches(job.projectId(),job.runId()) || !outcome.verificationId().equals(sourceId))
            throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_REVOKED");
        List<Map<String,Object>> currentReceipts;
        if(source.containsKey("evidenceRunScope")) {
            var scope=new JdbcTaskEpisodeRunScope(jdbc);
            if(!CanonicalJson.stringify(source.get("evidenceRunScope")).equals(CanonicalJson.stringify(evidenceRunScope(scope.runs(job.projectId(),outcome.taskEpisodeId())))))
                throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_RECEIPT_CHANGED");
            currentReceipts=scope.receipts(job.projectId(),outcome.taskEpisodeId());
        } else currentReceipts=jdbc.queryForList("""
                SELECT r.result_id,r.output_hash,r.full_output,r.status,r.run_id FROM ai_ops_tool_result r
                JOIN ai_ops_task_episode_turn t ON t.source_run_ref=r.run_id AND t.project_id=r.project_id
                WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED' LIMIT 201
                """,job.projectId(),outcome.taskEpisodeId());
        Map<String,Map<String,Object>> receiptsById=new HashMap<>();
        currentReceipts.forEach(r->receiptsById.put(text(r.get("result_id")),r));
        if (currentReceipts.size()!=maps(source.get("receipts")).size())
            throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_RECEIPT_CHANGED");
        for (var receipt:maps(source.get("receipts"))) {
            var current=receiptsById.get(text(receipt.get("resultId")));
            if (current==null || !receipt.get("outputHash").equals(current.get("output_hash"))
                    || !receipt.get("outputHash").equals(hash(text(current.get("full_output"))))
                    || !receipt.get("status").equals(current.get("status")) || !receipt.get("runId").equals(current.get("run_id")))
                throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_RECEIPT_CHANGED");
        }
        List<SkillEvolutionTraceEvent> trace=new ArrayList<>();
        for (var event:maps(source.get("trace"))) {
            var payload=object(event.get("payloadJson"));
            trace.add(new SkillEvolutionTraceEvent(text(event.get("eventType")),text(event.get("status")),
                    text(event.get("summary")),text(payload.get("content")),payload));
        }
        // Receipts are durable evidence even when a trace projector was delayed or compacted.
        for (var receipt:maps(source.get("receipts"))) {
            trace.add(new SkillEvolutionTraceEvent("TOOL_RECEIPT",text(receipt.get("status")),text(receipt.get("toolName")),
                    Map.of("evidenceId",text(receipt.get("resultId")),"resultId",text(receipt.get("resultId")),"outputHash",text(receipt.get("outputHash")))));
        }
        List<SkillEvolutionMessage> messages=maps(source.get("messages")).stream()
                .map(m->new SkillEvolutionMessage(text(m.get("role")),text(m.get("content")))).toList();
        String raw=CanonicalJson.stringify(source);
        return new SkillEvolutionInput(trace,messages,text(source.get("goal")),raw,hash(raw));
    }

    private List<Map<String,Object>> evidenceRunScope(List<JdbcTaskEpisodeRunScope.Run> runs) {
        return runs.stream().map(r->Map.<String,Object>of("runId",r.runId(),"rootRunId",r.rootRunId(),"revision",r.revision(),
                "depth",r.depth(),"status",text(r.row().get("status")),"workflowId",text(r.row().get("agent_id")),
                "workflowVersion",text(r.row().get("agent_version")),"definitionHash",text(r.row().get("agent_definition_hash")))).toList();
    }
    private List<Map<String,Object>> sourceReceipts(List<Map<String,Object>> rows) {
        return rows.stream().map(r->Map.<String,Object>of("runId",r.get("run_id"),"resultId",r.get("result_id"),
                "toolName",r.get("tool_name"),"source",r.get("source"),"status",r.get("status"),
                "outputHash",r.get("output_hash"),"fullOutput",r.get("full_output"))).toList();
    }

    void requireClaim(SkillEvolutionJobSnapshot job) {
        long count=jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id
                WHERE j.job_id=? AND j.run_id=? AND j.project_id=? AND j.session_id=? AND j.agent_id=? AND j.status='RUNNING'
                  AND j.attempts=? AND s.source_id=? AND s.lease_token=? AND s.epoch=?
                  AND s.lease_token<>'' AND s.lease_until_ms>UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000
                """,Long.class,job.jobId(),job.runId(),job.projectId(),job.sessionId(),job.agentId(),job.attempts()+1,
                job.sourceId(),job.leaseToken(),job.epoch());
        if (count!=1) throw new IllegalStateException("SKILL_EVOLUTION_CLAIM_LOST");
    }
    private Map<String,Object> stored(SkillEvolutionJobSnapshot job,String sourceId) {
        var rows=jdbc.queryForList("SELECT * FROM ai_ops_skill_evolution_source WHERE source_id=? AND project_id=? AND session_id=? AND run_id=?",
                sourceId,job.projectId(),job.sessionId(),job.runId());
        if (rows.size()!=1) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_MISSING");
        var row=rows.get(0); String raw=text(row.get("input_json")); var source=object(raw);
        if (!hash(raw).equals(row.get("input_hash")) || !CanonicalJson.stringify(source).equals(raw)
                || !sourceId.equals(source.get("sourceId")) || !job.projectId().equals(source.get("projectId"))
                || !job.sessionId().equals(source.get("sessionId")) || !job.runId().equals(source.get("runId"))
                || !row.get("episode_id").equals(source.get("episodeId")) || number(row.get("episode_revision"))!=number(source.get("revision")))
            throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_HASH_MISMATCH");
        return source;
    }
    private void bounded(List<?> rows,int limit) { if(rows.size()>limit) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_TOO_LARGE"); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> maps(Object value) { return (List<Map<String,Object>>)value; }
}
