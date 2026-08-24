package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillCanaryReviewModelPort;
import cn.lgs.orbisops.domain.evidence.service.SensitiveDataRedactionPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.runtime.contextbundle.service.RuntimeContextBundlePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Read-only, bounded snapshot. Changed receipts, corrections and later turns produce a new hash. */
final class JdbcSkillCanaryReviewInput {
    private final JdbcTemplate jdbc;
    JdbcSkillCanaryReviewInput(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    Optional<String> read(String project, String episode, String release) {
        var episodes=jdbc.queryForList("SELECT episode_id,revision,goal,outcome,verified_outcome_ref FROM ai_ops_task_episode WHERE project_id=? AND episode_id=?",project,episode);
        if(episodes.size()!=1) return Optional.empty();
        var turns=jdbc.queryForList("""
                SELECT t.source_run_ref,r.status,b.bundle_json,b.bundle_hash
                FROM ai_ops_task_episode_turn t
                LEFT JOIN ai_ops_agent_run r ON r.run_id=t.source_run_ref AND r.project_id=t.project_id AND r.session_id=t.session_id
                LEFT JOIN ai_ops_runtime_context_bundle b ON b.run_id=t.source_run_ref AND b.project_id=t.project_id
                WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED' ORDER BY t.id,b.id LIMIT 101
                """,project,episode);
        if(turns.isEmpty() || turns.size()>100) return Optional.empty();
        var facts=new ArrayList<Map<String,Object>>(); var ids=new HashSet<String>();
        var redaction=new SensitiveDataRedactionPolicy(); var policy=new RuntimeContextBundlePolicy();
        try {
            for(var turn:turns) {
                if(!Set.of("SUCCEEDED","FAILED","CANCELED").contains(text(turn.get("status")))) return Optional.empty();
                String raw=text(turn.get("bundle_json")); if(raw.isBlank()) return Optional.empty();
                var bundle=CanonicalJson.parseObject(raw);
                if(!policy.hashObject(bundle).equals(text(turn.get("bundle_hash")))) return Optional.empty();
                var context=new LinkedHashMap<String,Object>();
                for(String key:List.of("task","policy","changePackage","policyRefs","toolsetRefs","usedSkillVersionRefs","runtimeBoundaryHash"))
                    if(bundle.containsKey(key)) context.put(key,bundle.get(key));
                String run=text(turn.get("source_run_ref"));
                facts.add(Map.of("id",run,"runStatus",text(turn.get("status")),"bundleHash",text(turn.get("bundle_hash")),"context",redaction.redact(context)));
                ids.add(run);
            }
            var receipts=jdbc.queryForList("""
                    SELECT r.result_id,r.run_id,r.tool_name,r.status,r.output_hash,r.full_output
                    FROM ai_ops_tool_result r JOIN ai_ops_task_episode_turn t ON t.project_id=r.project_id AND t.source_run_ref=r.run_id
                    WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED' ORDER BY r.id LIMIT 501
                    """,project,episode);
            if(receipts.size()>500) return Optional.empty();
            for(var receipt:receipts) {
                if(Set.of("PENDING","STARTED","RUNNING","DISPATCHING","UNKNOWN","WAITING_APPROVAL").contains(text(receipt.get("status")))) return Optional.empty();
                String raw=text(receipt.get("full_output"));
                if(!hash(raw).equals(text(receipt.get("output_hash")))) return Optional.empty();
                String id=text(receipt.get("result_id")); ids.add(id);
                facts.add(Map.of("id",id,"runId",text(receipt.get("run_id")),"tool",text(receipt.get("tool_name")),
                        "status",text(receipt.get("status")),"outputHash",text(receipt.get("output_hash")),"output",redaction.redactText(raw)));
            }
            var messages=jdbc.queryForList("""
                    SELECT m.id,m.turn_id,m.role,m.content FROM ai_ops_chat_message m
                    JOIN ai_ops_task_episode_turn t ON t.project_id=m.project_id AND t.session_id=m.session_id AND t.source_run_ref=m.turn_id
                    WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED' ORDER BY m.id LIMIT 201
                    """,project,episode);
            if(messages.size()>200) return Optional.empty();
            for(var msg:messages) {
                String id="message:"+msg.get("id"); ids.add(id);
                String content=text(msg.get("content")), scrubbed=redaction.redactText(content);
                facts.add(Map.of("id",id,"runId",text(msg.get("turn_id")),"role",text(msg.get("role")),"content",scrubbed,
                        "sensitiveContentRedacted",!content.equals(scrubbed)));
            }
            String input=CanonicalJson.stringify(Map.of("projectId",project,"releaseId",release,
                    "episode",episodes.get(0),"reviewerVersion",SkillCanaryReviewModelPort.VERSION,
                    "facts",facts,"evidenceIds",ids.stream().sorted().toList()));
            return input.length()>250_000 ? Optional.empty() : Optional.of(input);
        } catch(IllegalArgumentException invalid) { return Optional.empty(); }
    }
}
