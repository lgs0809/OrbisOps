package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.ConversationMemoryWindow;
import cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** MySQL is authoritative; memory/Redis windows are disposable projections. Requires migration 077. */
@Repository
public class JdbcConversationMemoryRepository implements IConversationMemoryRepository {
    private final ObjectProvider<JdbcTemplate> templates;

    private final cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPort owners;
    public JdbcConversationMemoryRepository(ObjectProvider<JdbcTemplate> templates) {this(templates,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public JdbcConversationMemoryRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> templates,
            cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPort owners) {this.templates=templates;this.owners=owners;}

    @Override
    public ColdMemoryMessageSnapshot capture(ColdMemoryMessageSnapshot message, int bufferSize) {
        return transaction(() -> {
            JdbcTemplate jdbc = jdbc();
            String session = message.sessionId();
            String project = text(message.metadata().get("projectId"));
            String turn = text(message.metadata().get("turnId"));
            if (turn.isBlank()) turn = text(message.metadata().get("runId"));
            if (turn.isBlank()) turn = UUID.randomUUID().toString();
            String key = CanonicalObjectHasher.sha256(Map.of("turn", turn, "role", message.role(),
                    "user", text(message.userId()), "status", text(message.metadata().get("status"))));
            jdbc.update("""
                    INSERT INTO ai_ops_conversation_memory_state(session_id, project_id, user_id)
                    VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE session_id=VALUES(session_id)
                    """, session, project, text(message.userId()));
            Map<String, Object> state = jdbc.queryForMap(
                    "SELECT project_id, user_id, last_seq FROM ai_ops_conversation_memory_state WHERE session_id=? FOR UPDATE", session);
            if (!project.equals(text(state.get("project_id")))
                    || (project.isBlank() && !text(message.userId()).equals(text(state.get("user_id"))))) {
                throw new IllegalArgumentException("MEMORY_SESSION_SCOPE_MISMATCH");
            }
            List<ColdMemoryMessageSnapshot> duplicate = jdbc.query(
                    "SELECT * FROM ai_ops_chat_message WHERE session_id=? AND capture_key=?", this::message, session, key);
            if (!duplicate.isEmpty()) {
                if (!message.content().equals(duplicate.get(0).content())) {
                    throw new IllegalArgumentException("MEMORY_CAPTURE_KEY_CONFLICT");
                }
                return duplicate.get(0);
            }
            long sequence = ((Number) state.get("last_seq")).longValue() + 1;
            Map<String, Object> metadata = new LinkedHashMap<>(message.metadata());
            metadata.put("messageSeq", sequence);
            metadata.put("turn_index", sequence);
            metadata.put("turnId", turn);
            metadata.put("projectId", project);
            jdbc.update("""
                    INSERT INTO ai_ops_chat_message(session_id, user_id, role, content, metadata,
                        message_seq, project_id, turn_id, capture_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, session, message.userId(), message.role(), message.content(), JSON.toJSONString(metadata),
                    sequence, project, turn, key);
            jdbc.update("UPDATE ai_ops_conversation_memory_state SET last_seq=? WHERE session_id=?", sequence, session);
            for (String type : List.of("SEMANTIC", "EXTRACTION", "COMPRESSION")) {
                jdbc.update("""
                        INSERT INTO ai_ops_memory_post_processing(session_id, message_seq, task_type, buffer_size)
                        VALUES (?, ?, ?, ?)
                        """, session, sequence, type, Math.max(2, bufferSize));
            }
            return new ColdMemoryMessageSnapshot(session, message.userId(), message.role(), message.content(),
                    message.createdAt(), metadata);
        });
    }

    @Override
    public Optional<ConversationMemoryWindow> window(String sessionId, int limit, boolean newest) {
        return transaction(() -> {
            List<ConversationMemoryWindow> states = jdbc().query(
                    "SELECT * FROM ai_ops_conversation_memory_state WHERE session_id=?", (rs, row) ->
                            new ConversationMemoryWindow(sessionId, rs.getString("project_id"), rs.getString("user_id"),
                                    rs.getLong("last_seq"), rs.getLong("covered_seq"), rs.getLong("summary_revision"),
                                    rs.getString("summary_content"), protectedMessages(rs.getString("protected_messages_json")), List.of()),
                    sessionId);
            if (states.isEmpty()) return Optional.empty();
            ConversationMemoryWindow state = states.get(0);
            List<ColdMemoryMessageSnapshot> messages = new ArrayList<>(jdbc().query(
                    "SELECT * FROM ai_ops_chat_message WHERE session_id=? AND message_seq>? AND message_seq<=?"
                            + " ORDER BY message_seq " + (newest ? "DESC" : "ASC") + " LIMIT ?",
                    this::message, sessionId, state.coveredSeq(), state.lastSeq(), Math.max(2, Math.min(limit, 1000))));
            if (newest) Collections.reverse(messages);
            return Optional.of(new ConversationMemoryWindow(sessionId, state.projectId(), state.userId(), state.lastSeq(),
                    state.coveredSeq(), state.summaryRevision(), state.summaryContent(), state.protectedMessages(), messages));
        });
    }

    @Override
    public List<Long> existingSequences(String sessionId, String projectId, String userId, List<Long> sequences) {
        if (sequences == null || sequences.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>(List.of(sessionId, projectId, projectId, userId));
        args.addAll(sequences);
        return jdbc().query("""
                SELECT m.message_seq FROM ai_ops_chat_message m
                JOIN ai_ops_conversation_memory_state s ON s.session_id=m.session_id
                WHERE m.session_id=? AND s.project_id=? AND (?<>'' OR s.user_id=?) AND m.message_seq IN (
                """ + String.join(",", Collections.nCopies(sequences.size(), "?")) + ")",
                (rs, row) -> rs.getLong(1), args.toArray());
    }

    @Override public java.util.Set<String> visibleSemanticProjections(String session,String project,String user,
            List<cn.lgs.orbisops.domain.memory.model.SemanticProjectionReference> projections) {
        if(projections.isEmpty())return java.util.Set.of();
        if(owners!=null)try {owners.requireAccess(project,user);}catch(SecurityException denied){return java.util.Set.of();}
        var ids=projections.stream().map(cn.lgs.orbisops.domain.memory.model.SemanticProjectionReference::jobId).distinct().limit(500).toList();
        var args=new ArrayList<Object>(List.of(session,project,project,user));args.addAll(ids);
        record Visibility(long epoch,String status,String kind,String frozen,String actual,String owner) {}
        var states=new java.util.HashMap<Long,Visibility>();
        jdbc().query("SELECT j.id AS job_id,j.epoch,j.status AS job_status,j.result_kind,j.model_input_hash,c.user_id AS original_owner,m.* FROM ai_ops_memory_post_processing j JOIN ai_ops_chat_message m ON m.session_id=j.session_id AND m.message_seq=j.message_seq JOIN ai_ops_conversation_memory_state s ON s.session_id=m.session_id JOIN ai_ops_chat_session c ON c.session_id=s.session_id AND COALESCE(c.project_id,'')=s.project_id WHERE s.session_id=? AND s.project_id=? AND (?<>'' OR s.user_id=?) AND j.task_type='SEMANTIC' AND j.id IN ("+String.join(",",java.util.Collections.nCopies(ids.size(),"?"))+")",
                (org.springframework.jdbc.core.RowCallbackHandler)r->states.put(r.getLong("job_id"),new Visibility(r.getLong("epoch"),r.getString("job_status"),r.getString("result_kind"),r.getString("model_input_hash"),
                    cn.lgs.orbisops.domain.memory.service.MemoryProcessingSourcePolicy.active(message(r,0))?cn.lgs.orbisops.domain.memory.service.MemoryProcessingSourcePolicy.hash(message(r,0)):"",r.getString("original_owner"))),args.toArray());
        var visible=new java.util.HashSet<String>();
        for(var ref:projections) {
            var state=states.get(ref.jobId());
            if(state==null || ref.sourceHash().isBlank() || !ref.sourceHash().equals(state.frozen()) || !state.frozen().equals(state.actual())
                    || !java.util.Set.of("PENDING","RETRY_WAIT","RUNNING","COMPLETED").contains(state.status()))continue;
            if(owners!=null)try {owners.requireAccess(project,state.owner());}catch(SecurityException denied){continue;}
            if("LEXICAL".equals(ref.kind()) || "VECTOR".equals(ref.kind()) && "COMPLETED".equals(state.status())
                    && "VECTOR".equals(state.kind()) && ref.epoch()==state.epoch())visible.add(ref.documentId());
        }
        return visible;
    }

    @Override
    public boolean commitSummary(ConversationMemoryWindow expected, long coveredSeq, String content,
                                 List<ColdMemoryMessageSnapshot> protectedMessages, String source) {
        return commitSummaryInternal(expected, coveredSeq, content, protectedMessages, source, "");
    }

    @Override public boolean commitPendingModelSummary(ConversationMemoryWindow expected, long coveredSeq, String content,
            List<ColdMemoryMessageSnapshot> protectedMessages, String source, String modelInputJson) {
        if (text(modelInputJson).isBlank()) throw new IllegalArgumentException("MEMORY_MODEL_INPUT_REQUIRED");
        return commitSummaryInternal(expected, coveredSeq, content, protectedMessages, source, modelInputJson);
    }

    private boolean commitSummaryInternal(ConversationMemoryWindow expected, long coveredSeq, String content,
            List<ColdMemoryMessageSnapshot> protectedMessages, String source, String modelInputJson) {
        if (coveredSeq <= expected.coveredSeq() || coveredSeq > expected.lastSeq() || text(content).isBlank()) {
            throw new IllegalArgumentException("MEMORY_SUMMARY_COVERAGE_INVALID");
        }
        return transaction(() -> {
            String protectedJson = JSON.toJSONString(protectedMessages);
            int changed = jdbc().update("""
                    UPDATE ai_ops_conversation_memory_state
                    SET covered_seq=?, summary_revision=summary_revision+1, summary_content=?, protected_messages_json=?
                    WHERE session_id=? AND project_id=? AND summary_revision=? AND covered_seq=? AND last_seq>=?
                    """, coveredSeq, content, protectedJson, expected.sessionId(), expected.projectId(),
                    expected.summaryRevision(), expected.coveredSeq(), coveredSeq);
            if (changed != 1) return false;
            jdbc().update("""
                    INSERT INTO ai_ops_memory_summary_version(session_id, revision, project_id, covered_seq,
                        content, protected_messages_json, source) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, expected.sessionId(), expected.summaryRevision() + 1, expected.projectId(), coveredSeq,
                    content, protectedJson, source);
            if (!text(modelInputJson).isBlank()) {
                // Only a newly failed enabled model creates a supplement; old rule summaries are not scanned.
                jdbc().update("""
                        INSERT INTO ai_ops_memory_post_processing(session_id,message_seq,task_type,buffer_size,
                            next_attempt_ms,model_input_json,model_input_hash,last_error,result_kind)
                        VALUES (?,?,'COMPRESSION_MODEL',0,?,?,?,'MEMORY_SUMMARY_MODEL_DEFERRED','RULE_FALLBACK_MODEL_PENDING')
                        ON DUPLICATE KEY UPDATE id=id
                        """, expected.sessionId(), coveredSeq,
                        System.currentTimeMillis()+cn.lgs.orbisops.domain.shared.service.BackgroundModelRetryPolicy.delayMillis(
                                expected.sessionId()+":"+coveredSeq,1,30_000,0), modelInputJson,
                        CanonicalObjectHasher.sha256Text(modelInputJson));
            }
            // No replacement or deletion of live messages, even if new messages arrived while summarizing.
            return true;
        });
    }

    @Override
    public List<Long> pending(String sessionId, int limit, long nowMillis) {
        String filter = text(sessionId).isBlank() ? "" : " AND session_id=?";
        List<Object> args = new ArrayList<>(List.of(nowMillis, nowMillis));
        if (!filter.isBlank()) args.add(sessionId);
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbc().query("""
                SELECT id FROM ai_ops_memory_post_processing
                WHERE ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_ms<=?)
                    OR (status='RUNNING' AND lease_until_ms<=?))
                """ + filter + " ORDER BY id LIMIT ?", (rs, row) -> rs.getLong(1), args.toArray());
    }

    @Override
    public Optional<MemoryProcessingJob> claim(long id, String token, long nowMillis, long leaseMillis) {
        return transaction(() -> {
            var expired = jdbc().queryForList("SELECT attempts FROM ai_ops_memory_post_processing WHERE id=? AND status='RUNNING' AND lease_until_ms<=? FOR UPDATE",id,nowMillis);
            if (!expired.isEmpty()) {
                int attempts=((Number)expired.get(0).get("attempts")).intValue();
                long due=nowMillis+cn.lgs.orbisops.domain.shared.service.BackgroundModelRetryPolicy.delayMillis("memory:"+id,attempts,30_000,0);
                jdbc().update("UPDATE ai_ops_memory_post_processing SET status='RETRY_WAIT',next_attempt_ms=?,lease_token='',lease_until_ms=0,last_error='MEMORY_POST_PROCESSING_INTERRUPTED' WHERE id=?",due,id);
                return Optional.empty();
            }
            int changed = jdbc().update("""
                    UPDATE ai_ops_memory_post_processing SET status='RUNNING', attempts=attempts+1,
                        lease_token=?, epoch=epoch+1, lease_until_ms=?
                    WHERE id=? AND status IN ('PENDING','RETRY_WAIT') AND next_attempt_ms<=?
                    """, token, nowMillis + Math.max(1, leaseMillis), id, nowMillis);
            if (changed != 1) return Optional.empty();
            var claimed = jdbc().query("""
                    SELECT j.id AS job_id, j.task_type, j.buffer_size, j.attempts, j.lease_token, j.epoch, '' AS model_input_json, j.result_kind, m.*
                    FROM ai_ops_memory_post_processing j JOIN ai_ops_chat_message m
                        ON m.session_id=j.session_id AND m.message_seq=j.message_seq WHERE j.id=?
                    """, (rs, row) -> new MemoryProcessingJob(rs.getLong("job_id"), rs.getString("session_id"),
                    rs.getLong("message_seq"), rs.getString("task_type"), rs.getInt("buffer_size"),
                    rs.getInt("attempts"), rs.getString("lease_token"), rs.getLong("epoch"), message(rs, row), rs.getString("model_input_json"), rs.getString("result_kind")), id)
                    .stream().findFirst();
            if (claimed.isPresent() && "COMPRESSION_MODEL".equals(claimed.get().taskType())) {
                var payload = jdbc().queryForMap("SELECT model_input_json,model_input_hash FROM ai_ops_memory_post_processing WHERE id=?",id);
                var job = claimed.get();String input=text(payload.get("model_input_json"));
                claimed=Optional.of(new MemoryProcessingJob(job.id(),job.sessionId(),job.messageSeq(),job.taskType(),job.bufferSize(),job.attempts(),job.leaseToken(),job.epoch(),job.message(),input,job.resultKind()));
                if (input.isBlank() || !CanonicalObjectHasher.sha256Text(input).equals(text(payload.get("model_input_hash")))) {
                    jdbc().update("UPDATE ai_ops_memory_post_processing SET status='SOURCE_BLOCKED',lease_until_ms=0,last_error='MEMORY_MODEL_INPUT_HASH_MISMATCH' WHERE id=? AND lease_token=?",id,token);
                    return Optional.empty();
                }
            }
            if(claimed.isPresent() && "SEMANTIC".equals(claimed.get().taskType())) {
                var job=claimed.get();String actual=cn.lgs.orbisops.domain.memory.service.MemoryProcessingSourcePolicy.hash(job.message());
                String frozen=jdbc().queryForObject("SELECT model_input_hash FROM ai_ops_memory_post_processing WHERE id=?",String.class,id);
                if(text(frozen).isBlank()) {
                    jdbc().update("UPDATE ai_ops_memory_post_processing SET model_input_hash=? WHERE id=? AND model_input_hash=''",actual,id);frozen=actual;
                }
                if(!actual.equals(frozen)) {block(job,"MEMORY_SEMANTIC_SOURCE_CHANGED",nowMillis);return Optional.empty();}
                claimed=Optional.of(new MemoryProcessingJob(job.id(),job.sessionId(),job.messageSeq(),job.taskType(),job.bufferSize(),job.attempts(),job.leaseToken(),job.epoch(),job.message(),job.modelInputJson(),job.resultKind(),frozen));
            }
            if(claimed.isPresent() && "COMPRESSION_MODEL".equals(claimed.get().taskType())) {
                var job=claimed.get();
                try {
                    var input=cn.lgs.orbisops.domain.memory.model.MemorySummaryModelInput.parse(job.modelInputJson());
                    var state=window(job.sessionId(),2,false).orElse(null);
                    if(state!=null) input.requireScope(job.sessionId(),state.projectId(),state.userId());
                    else if(!input.sessionId().equals(job.sessionId())) throw new cn.lgs.orbisops.domain.memory.model.MemoryModelInputInvalidException("MEMORY_MODEL_SUMMARY_SCOPE_INVALID");
                } catch(cn.lgs.orbisops.domain.memory.model.MemoryModelInputInvalidException invalid) {
                    block(job,invalid.getMessage(),nowMillis);return Optional.empty();
                }
            }
            return claimed;
        });
    }

    @Override public boolean validSemanticSource(MemoryProcessingJob job,long now) {
        var retained=jdbc().query("SELECT m.* FROM ai_ops_memory_post_processing j JOIN ai_ops_chat_message m ON m.session_id=j.session_id AND m.message_seq=j.message_seq WHERE j.id=? AND j.status='RUNNING' AND j.lease_token=? AND j.epoch=? AND j.lease_until_ms>?",
                this::message,job.id(),job.leaseToken(),job.epoch(),now);
        if(retained.size()!=1 || !cn.lgs.orbisops.domain.memory.service.MemoryProcessingSourcePolicy.active(retained.get(0)) || !job.sourceHash().equals(cn.lgs.orbisops.domain.memory.service.MemoryProcessingSourcePolicy.hash(retained.get(0))))return false;
        if(owners!=null) {
            var scope=jdbc().queryForList("SELECT user_id,project_id FROM ai_ops_chat_session WHERE session_id=?",job.sessionId());
            if(scope.size()!=1 || !text(scope.get(0).get("project_id")).equals(text(job.message().metadata().get("projectId"))))return false;
            try {owners.requireAccess(text(scope.get(0).get("project_id")),text(scope.get(0).get("user_id")));}
            catch(SecurityException denied){return false;}
        }
        return true;
    }

    @Override public boolean block(MemoryProcessingJob job,String error,long nowMillis) {
        return jdbc().update("UPDATE ai_ops_memory_post_processing SET status='SOURCE_BLOCKED',lease_until_ms=0,last_error=? WHERE id=? AND status='RUNNING' AND lease_token=? AND epoch=? AND lease_until_ms>?",
                text(error).substring(0,Math.min(200,text(error).length())),job.id(),job.leaseToken(),job.epoch(),nowMillis)==1;
    }

    @Override
    public boolean complete(MemoryProcessingJob job, String resultKind, long nowMillis, Runnable localWrites) {
        return transaction(() -> {
            List<Long> locked = jdbc().query("""
                    SELECT id FROM ai_ops_memory_post_processing WHERE id=? AND status='RUNNING'
                        AND lease_token=? AND epoch=? AND lease_until_ms>? FOR UPDATE
                    """, (rs, row) -> rs.getLong(1), job.id(), job.leaseToken(), job.epoch(), nowMillis);
            if (locked.isEmpty()) return false;
            if (localWrites != null) localWrites.run();
            jdbc().update("""
                    UPDATE ai_ops_memory_post_processing SET status='COMPLETED', result_kind=?, last_error='',
                        lease_until_ms=0 WHERE id=? AND lease_token=? AND epoch=?
                    """, resultKind, job.id(), job.leaseToken(), job.epoch());
            return true;
        });
    }

    @Override
    public boolean retry(MemoryProcessingJob job, String errorCode, long nowMillis) {
        // Retain cumulative attempts and the last failure; a background model task has no lifetime quota.
        return jdbc().update("""
                UPDATE ai_ops_memory_post_processing SET status=?, next_attempt_ms=?, lease_until_ms=0, last_error=?
                WHERE id=? AND status='RUNNING' AND lease_token=? AND epoch=? AND lease_until_ms>?
                """, "RETRY_WAIT", nowMillis + cn.lgs.orbisops.domain.shared.service.BackgroundModelRetryPolicy.delayMillis(
                        "memory:"+job.id(), job.attempts(), 30_000, 0),
                text(errorCode).substring(0, Math.min(200, text(errorCode).length())),
                job.id(), job.leaseToken(), job.epoch(), nowMillis) == 1;
    }

    @Override public boolean defer(MemoryProcessingJob job, String resultKind, String errorCode, long nowMillis, Runnable localWrites) {
        return transaction(() -> {
            var owned = jdbc().queryForList("SELECT id FROM ai_ops_memory_post_processing WHERE id=? AND status='RUNNING' AND lease_token=? AND epoch=? AND lease_until_ms>? FOR UPDATE",
                    job.id(),job.leaseToken(),job.epoch(),nowMillis);
            if (owned.isEmpty()) return false;
            if (localWrites != null) localWrites.run();
            jdbc().update("UPDATE ai_ops_memory_post_processing SET result_kind=? WHERE id=?",resultKind,job.id());
            return retry(job,errorCode,nowMillis);
        });
    }

    @Override public boolean replacePendingModelSummary(cn.lgs.orbisops.domain.memory.model.MemorySummaryModelInput input, String content) {
        return transaction(() -> {
            var states = jdbc().queryForList("""
                    SELECT s.* FROM ai_ops_conversation_memory_state s JOIN ai_ops_memory_summary_version v
                      ON v.session_id=s.session_id AND v.revision=s.summary_revision AND v.project_id=s.project_id
                    WHERE s.session_id=? AND s.project_id=? AND s.user_id=? AND s.covered_seq=? AND s.summary_revision=?
                      AND v.source='context_compressor_model_pending' FOR UPDATE
                    """,input.sessionId(),input.projectId(),input.userId(),input.coveredSeq(),input.summaryRevision());
            if (states.isEmpty()) return false;
            var state=states.get(0);
            jdbc().update("UPDATE ai_ops_conversation_memory_state SET summary_revision=summary_revision+1,summary_content=? WHERE session_id=? AND summary_revision=?",
                    content,input.sessionId(),input.summaryRevision());
            jdbc().update("""
                    INSERT INTO ai_ops_memory_summary_version(session_id,revision,project_id,covered_seq,content,protected_messages_json,source)
                    VALUES (?,?,?,?,?,?,'llm_context_compressor')
                    """,input.sessionId(),input.summaryRevision()+1,input.projectId(),input.coveredSeq(),content,state.get("protected_messages_json"));
            return true;
        });
    }

    private ColdMemoryMessageSnapshot message(ResultSet rs, int row) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String raw = rs.getString("metadata");
        if (!text(raw).isBlank()) metadata.putAll(JSON.parseObject(raw));
        metadata.put("messageSeq", rs.getLong("message_seq"));
        metadata.put("turn_index", rs.getLong("message_seq"));
        metadata.put("projectId", rs.getString("project_id"));
        metadata.put("turnId", rs.getString("turn_id"));
        return new ColdMemoryMessageSnapshot(rs.getString("session_id"), rs.getString("user_id"),
                rs.getString("role"), rs.getString("content"), rs.getString("create_time"), metadata);
    }

    private List<ColdMemoryMessageSnapshot> protectedMessages(String raw) {
        if (text(raw).isBlank()) return List.of();
        return JSON.parseArray(raw, ColdMemoryMessageSnapshot.class);
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate jdbc = templates.getIfAvailable();
        if (jdbc == null) throw new IllegalStateException("CONVERSATION_MEMORY_DATABASE_UNAVAILABLE");
        return jdbc;
    }

    private <T> T transaction(Supplier<T> action) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc().getDataSource()))
                .execute(status -> action.get());
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
