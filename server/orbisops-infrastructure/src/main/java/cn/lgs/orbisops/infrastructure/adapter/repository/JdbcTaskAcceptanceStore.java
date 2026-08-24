package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.episode.TaskAcceptancePort;
import cn.lgs.orbisops.application.skill.SkillTaskOutcomePort;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.TaskAcceptancePolicy;
import cn.lgs.orbisops.domain.skill.service.TaskReceiptEvidencePolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Revision-bound acceptance ledger. No API-supplied success flag, source identity or outcome is trusted. */
@Repository
public class JdbcTaskAcceptanceStore implements TaskAcceptancePort, SkillTaskOutcomePort {
    private final ObjectProvider<JdbcTemplate> templates;
    private final ObjectProvider<PlatformTransactionManager> transactions;
    private final TaskAcceptancePolicy policy = new TaskAcceptancePolicy();
    private final TaskReceiptEvidencePolicy evidencePolicy = new TaskReceiptEvidencePolicy();
    public JdbcTaskAcceptanceStore(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> templates,
            @Qualifier("mysqlTransactionManager") ObjectProvider<PlatformTransactionManager> transactions) {
        this.templates = templates; this.transactions = transactions;
    }
    private JdbcTemplate jdbc() { return templates.getObject(); }
    private Map<String,Object> episode(String project, String id, String actor, boolean admin) {
        var rows = jdbc().queryForList("SELECT e.*,s.user_id FROM ai_ops_task_episode e JOIN ai_ops_chat_session s "
                + "ON s.session_id=e.session_id AND s.project_id=e.project_id WHERE e.project_id=? AND e.episode_id=?", project,id);
        if (rows.size()!=1 || (!admin && !actor.equals(text(rows.get(0).get("user_id")))))
            throw new SecurityException("TASK_ACCEPTANCE_SCOPE_FORBIDDEN");
        return rows.get(0);
    }
    private long pending(String project, String session) {
        return jdbc().queryForObject("""
                SELECT COUNT(*) FROM ai_ops_chat_message m
                LEFT JOIN ai_ops_task_episode_turn t ON t.session_id=m.session_id AND t.source_run_ref=m.turn_id
                WHERE m.project_id=? AND m.session_id=? AND m.role='user' AND (t.id IS NULL OR t.status<>'ASSIGNED')
                """, Long.class,project,session);
    }
    @Override public Map<String,Object> inspect(String project, String id, String actor, boolean admin) {
        var e = episode(project,id,actor,admin);
        var receipts = new JdbcTaskEpisodeRunScope(jdbc()).receipts(project,id).stream()
                .filter(r->"MCP_REMOTE_TOOL".equals(r.get("source")))
                .map(r->Map.of("result_id",r.get("result_id"),"output_hash",r.get("output_hash"),
                        "status",r.get("status"),"tool_name",r.get("tool_name"),"run_id",r.get("run_id"))).toList();
        var history = jdbc().queryForList("SELECT acceptance_id,episode_revision,outcome,record_json,record_hash,created_by,created_at "
                + "FROM ai_ops_task_acceptance WHERE project_id=? AND episode_id=? ORDER BY created_at DESC LIMIT 30", project,id);
        return Map.of("episodeId",id,"goal",e.get("goal"),"revision",e.get("revision"),"outcome",e.get("outcome"),
                "pendingTurns",pending(project,text(e.get("session_id"))),"receipts",receipts,"history",history);
    }
    @Override public Map<String,Object> verify(String project,String id,TaskAcceptanceRequest request,String actor,boolean admin) {
        policy.validate(request);
        var transaction = new TransactionTemplate(transactions.getObject());
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction.execute(tx -> verifyRequired(project,id,request,actor,admin));
    }
    @Override public Map<String,Object> draftEvidence(String project, String id, String actor, boolean admin) {
        var e = episode(project,id,actor,admin);
        var rows = new JdbcTaskEpisodeRunScope(jdbc()).receipts(project,id).stream()
                .filter(r->"MCP_REMOTE_TOOL".equals(r.get("source")) && "SUCCEEDED".equals(r.get("status"))).toList();
        if (rows.size()>100) throw new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_TOO_LARGE");
        List<Map<String,Object>> receipts = new ArrayList<>();
        for (var row : rows) {
            String raw = text(row.get("full_output"));
            if (!hash(raw).equals(row.get("output_hash"))) throw new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_HASH_MISMATCH");
            var body = evidencePolicy.content(project, CanonicalJson.parseObject(raw));
            if (body.isEmpty()) continue;
            var content = body.get();
            receipts.add(Map.of("resultId",row.get("result_id"),"toolName",row.get("tool_name"),
                    "outputHash",row.get("output_hash"),"revision",row.get("episode_revision"),"content",content));
        }
        var requests=jdbc().queryForList("""
                SELECT m.content,t.episode_revision AS revision FROM ai_ops_chat_message m
                JOIN ai_ops_task_episode_turn t ON t.source_run_ref=m.turn_id AND t.project_id=m.project_id AND t.session_id=m.session_id
                WHERE t.project_id=? AND t.episode_id=? AND t.status='ASSIGNED' AND m.role='user'
                ORDER BY m.message_seq LIMIT 101
                """,project,id);
        if (requests.size()>100) throw new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_TOO_LARGE");
        return Map.of("goal",e.get("goal"),"revision",e.get("revision"),"originalUserRequests",requests,
                "pendingTurns",pending(project,text(e.get("session_id"))),"receipts",receipts);
    }
    private Map<String,Object> verifyRequired(String project,String id,TaskAcceptanceRequest request,String actor,boolean admin) {
        var initial = episode(project,id,actor,admin);
        String session = text(initial.get("session_id"));
        jdbc().queryForMap("SELECT * FROM ai_ops_task_episode_session WHERE session_id=? AND project_id=? FOR UPDATE",session,project);
        var e = jdbc().queryForMap("SELECT * FROM ai_ops_task_episode WHERE episode_id=? AND project_id=? FOR UPDATE",id,project);
        String requestHash = hash(CanonicalJson.stringify(Map.of("revision",request.revision(),"goalReview",request.goalReview(),"criteria",request.criteria())));
        // A locking read must see the preceding request after the session lock, even under an outer REPEATABLE READ transaction.
        var old = jdbc().queryForList("SELECT request_hash,record_json,record_hash FROM ai_ops_task_acceptance WHERE project_id=? AND episode_id=? AND request_id=? FOR UPDATE",project,id,request.requestId());
        if (!old.isEmpty()) {
            if (!requestHash.equals(old.get(0).get("request_hash"))) throw new IllegalStateException("TASK_ACCEPTANCE_REQUEST_CONFLICT");
            if (!hash(text(old.get(0).get("record_json"))).equals(old.get(0).get("record_hash")))
                throw new IllegalStateException("TASK_ACCEPTANCE_RECORD_HASH_MISMATCH");
            return object(text(old.get(0).get("record_json")));
        }
        if (number(e.get("revision")) != request.revision()) throw new IllegalStateException("TASK_ACCEPTANCE_REVISION_CHANGED");
        if (pending(project,session) > 0) throw new IllegalStateException("TASK_ACCEPTANCE_PENDING_TURNS");
        var unfinished = jdbc().queryForObject("""
                SELECT COUNT(*) FROM ai_ops_task_episode_turn t LEFT JOIN ai_ops_agent_run r ON r.run_id=t.source_run_ref
                WHERE t.project_id=? AND t.episode_id=? AND (r.run_id IS NULL OR r.project_id<>t.project_id OR r.status NOT IN ('SUCCEEDED','FAILED','CANCELED'))
                """,Long.class,project,id);
        var scope = new JdbcTaskEpisodeRunScope(jdbc());
        var scopedRuns = scope.runs(project,id);
        var scopedReceipts = scope.receipts(project,id);
        boolean unresolvedChild = scopedRuns.stream().anyMatch(r->!Set.of("SUCCEEDED","FAILED","CANCELED").contains(text(r.row().get("status"))));
        boolean activeTools = scopedReceipts.stream().anyMatch(r->Set.of("PENDING","STARTED","RUNNING","DISPATCHING","UNKNOWN","WAITING_APPROVAL").contains(text(r.get("status"))));
        if (unfinished>0 || unresolvedChild || activeTools) throw new IllegalStateException("TASK_ACCEPTANCE_EXECUTION_UNRESOLVED");
        List<Map<String,Object>> checks = new ArrayList<>();
        List<Map<String,Object>> conditions = new ArrayList<>();
        boolean currentRevisionEvidence = false;
        for (var criterion : request.criteria()) {
            var rows = scopedReceipts.stream().filter(r->criterion.resultId().equals(r.get("result_id"))
                    && "MCP_REMOTE_TOOL".equals(r.get("source"))).toList();
            if (rows.size()!=1) throw new SecurityException("TASK_ACCEPTANCE_EVIDENCE_SCOPE_FORBIDDEN");
            var receipt = rows.get(0); String raw = text(receipt.get("full_output"));
            currentRevisionEvidence |= number(receipt.get("episode_revision")) == request.revision();
            if (!criterion.outputHash().equals(receipt.get("output_hash")) || !criterion.outputHash().equals(hash(raw)))
                throw new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_HASH_MISMATCH");
            if (!"SUCCEEDED".equals(receipt.get("status"))) throw new IllegalStateException("TASK_ACCEPTANCE_TOOL_NOT_SUCCEEDED");
            var normalized = evidencePolicy.content(project, CanonicalJson.parseObject(raw))
                    .orElseThrow(() -> new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_NOT_AVAILABLE"));
            checks.add(policy.check(criterion,normalized));
            conditions.add(evidencePolicy.condition(text(receipt.get("tool_name")), normalized));
        }
        if (!currentRevisionEvidence) throw new IllegalStateException("TASK_ACCEPTANCE_CURRENT_REVISION_EVIDENCE_REQUIRED");
        String outcome = checks.stream().anyMatch(c->"FAILED".equals(c.get("verdict"))) ? "FAILED"
                : checks.stream().allMatch(c->"PASSED".equals(c.get("verdict"))) ? "SUCCEEDED" : "UNKNOWN";
        String sourceRun = jdbc().queryForObject("SELECT source_run_ref FROM ai_ops_task_episode_turn WHERE project_id=? AND episode_id=? "
                + "AND status='ASSIGNED' ORDER BY episode_revision DESC LIMIT 1",String.class,project,id);
        String acceptance = "task-acceptance-"+UUID.randomUUID();
        String conditionKey = hash(CanonicalJson.stringify(conditions.stream().map(CanonicalJson::stringify).distinct().sorted().toList()));
        Map<String,Object> record = new LinkedHashMap<>();
        record.put("acceptanceId",acceptance); record.put("projectId",project); record.put("episodeId",id);
        record.put("revision",request.revision()); record.put("goal",e.get("goal")); record.put("goalReview",request.goalReview());
        record.put("checks",checks); record.put("outcome",outcome); record.put("sourceRunId",sourceRun);
        record.put("conditionKey",conditionKey); record.put("reviewedBy",actor); record.put("mode","REVIEWER_ASSERTIONS_ON_REAL_RECEIPTS");
        String encoded = CanonicalJson.stringify(record);
        jdbc().update("INSERT INTO ai_ops_task_acceptance(acceptance_id,project_id,episode_id,episode_revision,request_id,request_hash,condition_key,"
                + "outcome,source_run_id,record_json,record_hash,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                acceptance,project,id,request.revision(),request.requestId(),requestHash,conditionKey,outcome,sourceRun,encoded,hash(encoded),actor);
        jdbc().update("UPDATE ai_ops_task_episode SET outcome=?,verified_outcome_ref=? WHERE project_id=? AND episode_id=? AND revision=?",
                outcome,acceptance,project,id,request.revision());
        return record;
    }
    @Override public VerifiedTaskOutcome verifiedSuccess(String project,String run) {
        return new JdbcVerifiedTaskOutcomeReader(jdbc()).read(project,run);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value) {
        return value instanceof Map<?,?> m ? (Map<String,Object>)m : Map.of();
    }
}
