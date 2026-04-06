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

    public JdbcConversationMemoryRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> templates) {
        this.templates = templates;
    }

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

    @Override
    public boolean commitSummary(ConversationMemoryWindow expected, long coveredSeq, String content,
                                 List<ColdMemoryMessageSnapshot> protectedMessages, String source) {
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
            int changed = jdbc().update("""
                    UPDATE ai_ops_memory_post_processing SET status='RUNNING', attempts=attempts+1,
                        lease_token=?, epoch=epoch+1, lease_until_ms=?
                    WHERE id=? AND ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_ms<=?)
                        OR (status='RUNNING' AND lease_until_ms<=?))
                    """, token, nowMillis + Math.max(1, leaseMillis), id, nowMillis, nowMillis);
            if (changed != 1) return Optional.empty();
            return jdbc().query("""
                    SELECT j.id AS job_id, j.task_type, j.buffer_size, j.attempts, j.lease_token, j.epoch, m.*
                    FROM ai_ops_memory_post_processing j JOIN ai_ops_chat_message m
                        ON m.session_id=j.session_id AND m.message_seq=j.message_seq WHERE j.id=?
                    """, (rs, row) -> new MemoryProcessingJob(rs.getLong("job_id"), rs.getString("session_id"),
                    rs.getLong("message_seq"), rs.getString("task_type"), rs.getInt("buffer_size"),
                    rs.getInt("attempts"), rs.getString("lease_token"), rs.getLong("epoch"), message(rs, row)), id)
                    .stream().findFirst();
        });
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
        // Keep the durable failure visible after eight attempts; an operator can explicitly replay it.
        return jdbc().update("""
                UPDATE ai_ops_memory_post_processing SET status=?, next_attempt_ms=?, lease_until_ms=0, last_error=?
                WHERE id=? AND status='RUNNING' AND lease_token=? AND epoch=? AND lease_until_ms>?
                """, job.attempts() >= 8 ? "FAILED" : "RETRY_WAIT", nowMillis + Math.min(60_000L, 1000L * job.attempts()),
                text(errorCode).substring(0, Math.min(200, text(errorCode).length())),
                job.id(), job.leaseToken(), job.epoch(), nowMillis) == 1;
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
