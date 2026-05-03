package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.episode.TaskEpisodeModelPort;
import cn.lgs.orbisops.application.episode.TaskEpisodeStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

@Repository
public class JdbcTaskEpisodeStore implements TaskEpisodeStore {
    // The model may use its full minute; keep a further minute for fenced result persistence.
    private static final long LEASE_MS = (TaskEpisodeModelPort.CALL_TIMEOUT_SECONDS + 60L) * 1000;
    private static final int MAX_ATTEMPTS = 5;
    // Invalid model output stays bounded. Temporary infrastructure failures remain durable
    // and retry independently of the conversation, with a one-hour maximum local backoff.
    private static final Set<String> TRANSIENT_FAILURES = Set.of("EPISODE_MODEL_TIMEOUT",
            "EPISODE_MODEL_RATE_LIMITED", "EPISODE_MODEL_UNAVAILABLE", "EPISODE_MODEL_INTERRUPTED",
            "EPISODE_MODEL_CAPACITY", "EPISODE_ATTEMPTS_EXHAUSTED");
    private static String retryEligibility(String alias) {
        String prefix = alias.isEmpty() ? "" : alias + ".";
        return "(" + prefix + "status<>'RETRY_EXHAUSTED' OR " + prefix + "attempts<5 OR "
                + prefix + "last_error IN (" + TRANSIENT_FAILURES.stream().sorted()
                .map(reason -> "'" + reason + "'").collect(java.util.stream.Collectors.joining(",")) + "))";
    }
    private static long backoff(int attempts) {
        return Math.min(3600000L, 30000L << Math.min(7, Math.max(0, attempts - 1)));
    }
    private static final String GENERATOR_HASH = hash(TaskEpisodeModelPort.GENERATOR + ":" + TaskEpisodeModelPort.MODEL + ":PROGRESS:UNKNOWN:v1");
    private final ObjectProvider<JdbcTemplate> templates;
    private final ObjectProvider<PlatformTransactionManager> transactions;
    public JdbcTaskEpisodeStore(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> templates,
            @Qualifier("mysqlTransactionManager") ObjectProvider<PlatformTransactionManager> transactions) {
        this.templates = templates; this.transactions = transactions;
    }
    private JdbcTemplate jdbc() { return templates.getObject(); }
    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactions.getObject()).execute(status -> work.get()); }
    private Map<String, Object> lockSession(String session, String project) {
        jdbc().update("INSERT INTO ai_ops_task_episode_session(session_id,project_id) VALUES (?,?) "
                + "ON DUPLICATE KEY UPDATE session_id=VALUES(session_id)", session, project);
        var state = jdbc().queryForMap("SELECT * FROM ai_ops_task_episode_session WHERE session_id=? FOR UPDATE", session);
        if (!project.equals(text(state.get("project_id")))) throw new IllegalArgumentException("EPISODE_SCOPE_CONFLICT");
        return state;
    }
    @Override public void captureContext(String project, String session, String run, Map<String, Object> snapshot) {
        if (project == null || project.isBlank() || session == null || session.isBlank() || run == null || run.isBlank()) return;
        String input = json(snapshot);
        jdbc().update("INSERT INTO ai_ops_task_episode_context(run_id,project_id,session_id,snapshot_json,snapshot_hash) VALUES (?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE run_id=VALUES(run_id)", run, project, session, input, hash(input));
        // On approval resume the initial context stays immutable; never overwrite it with a later context.
        var old = jdbc().queryForMap("SELECT project_id,session_id FROM ai_ops_task_episode_context WHERE run_id=?", run);
        if (!project.equals(old.get("project_id")) || !session.equals(old.get("session_id")))
            throw new IllegalArgumentException("EPISODE_CONTEXT_SCOPE_CONFLICT");
    }
    @Override public int discover(int limit) {
        var candidates = jdbc().queryForList("""
                SELECT m.project_id,m.session_id,MIN(m.message_seq) AS turn_seq,m.turn_id
                FROM ai_ops_chat_message m
                WHERE m.role='user' AND m.project_id<>'' AND m.turn_id<>'' AND m.message_seq IS NOT NULL
                  AND NOT EXISTS(SELECT 1 FROM ai_ops_task_episode_turn t WHERE t.session_id=m.session_id AND t.source_run_ref=m.turn_id)
                GROUP BY m.project_id,m.session_id,m.turn_id ORDER BY MIN(m.id) LIMIT ?
                """, Math.max(1, Math.min(limit, 1000)));
        int discovered = 0;
        // INSERT ... SELECT acquired shared/gap locks on the live chat table and deadlocked
        // concurrent scanners. Read candidates without locks, then serialize only the new projection.
        for (var candidate : candidates) discovered += tx(() -> {
            lockSession(text(candidate.get("session_id")), text(candidate.get("project_id")));
            return jdbc().update("""
                    INSERT INTO ai_ops_task_episode_turn(project_id,session_id,turn_seq,source_run_ref,classifier_revision)
                    VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE source_run_ref=VALUES(source_run_ref)
                    """, candidate.get("project_id"), candidate.get("session_id"), candidate.get("turn_seq"),
                    candidate.get("turn_id"), TaskEpisodeModelPort.CLASSIFIER);
        });
        return discovered;
    }
    @Override public List<String> pendingSessions(long now, int limit) {
        return jdbc().queryForList("""
                SELECT t.session_id FROM ai_ops_task_episode_turn t
                WHERE t.status NOT IN ('ASSIGNED','SOURCE_BLOCKED') AND %s AND t.next_attempt_ms<=? AND t.lease_until_ms<=?
                AND NOT EXISTS(SELECT 1 FROM ai_ops_task_episode_turn p WHERE p.session_id=t.session_id AND p.turn_seq<t.turn_seq AND p.status<>'ASSIGNED')
                ORDER BY t.next_attempt_ms,t.id LIMIT ?
                """.formatted(retryEligibility("t")), String.class, now, now, limit);
    }
    @Override public Optional<Claim> claimTurn(String session, String owner, long now, boolean available) {
        return tx(() -> {
            var initial = jdbc().queryForList("SELECT * FROM ai_ops_task_episode_turn WHERE session_id=? AND status<>'ASSIGNED' ORDER BY turn_seq LIMIT 1", session);
            if (initial.isEmpty()) return Optional.empty();
            var state = lockSession(session, text(initial.get(0).get("project_id")));
            var pending = jdbc().queryForList("SELECT * FROM ai_ops_task_episode_turn WHERE session_id=? AND status<>'ASSIGNED' ORDER BY turn_seq LIMIT 1 FOR UPDATE", session);
            if (pending.isEmpty()) return Optional.empty();
            var row = pending.get(0);
            if (!eligible(row, now)) return Optional.empty();
            if (exhausted(row, "TURN", now)) return Optional.empty();
            // Also fence a scanner that has not discovered an earlier durable user turn yet.
            int earlier = jdbc().queryForObject("""
                    SELECT COUNT(*) FROM ai_ops_chat_message m WHERE m.session_id=? AND m.role='user' AND m.turn_id<>'' AND m.message_seq<?
                    AND NOT EXISTS(SELECT 1 FROM ai_ops_task_episode_turn t WHERE t.session_id=m.session_id AND t.source_run_ref=m.turn_id AND t.status='ASSIGNED')
                    """, Integer.class, session, row.get("turn_seq"));
            if (earlier > 0) return Optional.empty();
            if (text(row.get("input_json")).isBlank()) {
                try {
                    var source = new JdbcTaskEpisodeSource(jdbc()).freeze(row, text(state.get("active_episode_id")));
                    if (source.isEmpty()) {
                        jdbc().update("UPDATE ai_ops_task_episode_turn SET status='WAITING_TURN',next_attempt_ms=? WHERE id=?", now + 5000, row.get("id"));
                        return Optional.empty();
                    }
                    String input = json(source.get());
                    jdbc().update("UPDATE ai_ops_task_episode_turn SET input_json=?,input_hash=?,end_seq=?,context_fidelity=? WHERE id=?",
                            input, hash(input), source.get().get("endSeq"), source.get().get("contextFidelity"), row.get("id"));
                    row.put("input_json", input); row.put("input_hash", hash(input));
                } catch (IllegalStateException invalid) {
                    String reason = invalid.getMessage() != null && invalid.getMessage().matches("EPISODE_[A-Z_]+") ? invalid.getMessage() : "EPISODE_SOURCE_INVALID";
                    jdbc().update("UPDATE ai_ops_task_episode_turn SET status='SOURCE_BLOCKED',last_error=? WHERE id=?", reason, row.get("id"));
                    return Optional.empty();
                }
            }
            if (!validInput(row, "TURN")) return Optional.empty();
            return Optional.of(claim(row, "TURN", session, owner, now, available));
        });
    }
    private boolean eligible(Map<String, Object> row, long now) {
        if (Set.of("ASSIGNED", "SUCCEEDED", "SOURCE_BLOCKED").contains(text(row.get("status")))) return false;
        if ("RETRY_EXHAUSTED".equals(text(row.get("status"))) && number(row.get("attempts")) >= MAX_ATTEMPTS
                && !TRANSIENT_FAILURES.contains(text(row.get("last_error")))) return false;
        return number(row.get("lease_until_ms")) <= now && number(row.get("next_attempt_ms")) <= now;
    }
    private Claim claim(Map<String, Object> row, String kind, String session, String owner, long now, boolean available) {
        String table = table(kind); long epoch = number(row.get("epoch")) + 1;
        int attempts = (int) Math.min(Integer.MAX_VALUE, number(row.get("attempts")) + (available ? 1 : 0));
        jdbc().update("UPDATE " + table + " SET status='RUNNING',lease_owner=?,epoch=?,attempts=?,lease_until_ms=? WHERE id=?",
                owner, epoch, attempts, now + LEASE_MS, row.get("id"));
        return new Claim(number(row.get("id")), kind, session, text(row.get("episode_id")),
                row.get("revision") == null ? 0 : number(row.get("revision")), owner, epoch,
                text(row.get("input_json")), text(row.get("input_hash")), attempts);
    }
    private boolean exhausted(Map<String, Object> row, String kind, long now) {
        if (number(row.get("attempts")) < MAX_ATTEMPTS) return false;
        if ("RUNNING".equals(text(row.get("status")))) {
            // Expired workers must not turn repeated crashes into a tight replay loop.
            jdbc().update("UPDATE " + table(kind) + " SET status='PENDING_RETRY',lease_until_ms=0,"
                            + "last_error='EPISODE_MODEL_INTERRUPTED',next_attempt_ms=? WHERE id=?",
                    now + backoff((int) number(row.get("attempts"))), row.get("id"));
            return true;
        }
        if (TRANSIENT_FAILURES.contains(text(row.get("last_error")))) return false;
        jdbc().update("UPDATE " + table(kind) + " SET status='RETRY_EXHAUSTED',lease_until_ms=0,last_error='EPISODE_ATTEMPTS_EXHAUSTED' WHERE id=?", row.get("id"));
        return true;
    }
    private boolean validInput(Map<String, Object> row, String kind) {
        if (hash(text(row.get("input_json"))).equals(text(row.get("input_hash")))) return true;
        jdbc().update("UPDATE " + table(kind) + " SET status='SOURCE_BLOCKED',lease_until_ms=0,last_error='EPISODE_INPUT_HASH_MISMATCH' WHERE id=?", row.get("id"));
        return false;
    }
    private String table(String kind) {
        return switch (kind) { case "TURN" -> "ai_ops_task_episode_turn"; case "CONSOLIDATION" -> "ai_ops_episode_consolidation_job";
            default -> throw new IllegalArgumentException("EPISODE_CLAIM_KIND_INVALID"); };
    }
    private Optional<Map<String, Object>> fenced(Claim claim, long now) {
        return jdbc().queryForList("SELECT * FROM " + table(claim.kind())
                + " WHERE id=? AND status='RUNNING' AND lease_owner=? AND epoch=? AND lease_until_ms>? FOR UPDATE",
                claim.id(), claim.owner(), claim.epoch(), now).stream().findFirst();
    }
    @Override public boolean assign(Claim claim, TaskEpisodeModelPort.Decision decision, long now) {
        if (!"TURN".equals(claim.kind())) throw new IllegalArgumentException("EPISODE_TURN_CLAIM_REQUIRED");
        return tx(() -> {
            var input = object(claim.inputJson()); String project = text(input.get("projectId"));
            long sourceActivity = input.get("sourceLastActivityMs") == null ? now : number(input.get("sourceLastActivityMs"));
            var state = lockSession(claim.sessionId(), project);
            var current = fenced(claim, now); if (current.isEmpty()) return false;
            var row = current.get();
            long prior = jdbc().queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode_turn WHERE session_id=? AND turn_seq<? AND status<>'ASSIGNED'",
                    Long.class, claim.sessionId(), row.get("turn_seq"));
            if (prior != 0 || number(state.get("assigned_through_seq")) >= number(row.get("turn_seq"))) return false;
            String active = text(state.get("active_episode_id")); String episode; long revision;
            if ("CREATE".equals(decision.action())) {
                if (!active.isBlank()) enqueue(active, "BOUNDARY", now);
                episode = "task-episode-" + UUID.randomUUID(); revision = 1;
                jdbc().update("INSERT INTO ai_ops_task_episode(episode_id,project_id,session_id,goal,last_activity_ms) VALUES (?,?,?,?,?)",
                        episode, project, claim.sessionId(), decision.goal(), sourceActivity);
                jdbc().update("UPDATE ai_ops_task_episode_session SET active_episode_id=? WHERE session_id=?", episode, claim.sessionId());
            } else {
                episode = decision.episodeId();
                var targets = jdbc().queryForList("SELECT * FROM ai_ops_task_episode WHERE episode_id=? AND project_id=? AND session_id=? FOR UPDATE",
                        episode, project, claim.sessionId());
                if (targets.isEmpty()) throw new IllegalArgumentException("EPISODE_CONTINUE_SCOPE_INVALID");
                revision = number(targets.get(0).get("revision")) + 1;
                jdbc().update("UPDATE ai_ops_task_episode SET revision=?,last_activity_ms=GREATEST(last_activity_ms,?),outcome='UNKNOWN',verified_outcome_ref='' WHERE episode_id=?",
                        revision, sourceActivity, episode);
            }
            String result = json(Map.of("action", decision.action(), "episodeId", episode, "goal", decision.goal(), "reason", decision.reason()));
            jdbc().update("UPDATE ai_ops_task_episode_turn SET status='ASSIGNED',episode_id=?,episode_revision=?,decision_json=?,lease_until_ms=0,last_error='' WHERE id=?",
                    episode, revision, result, claim.id());
            jdbc().update("UPDATE ai_ops_task_episode_session SET assigned_through_seq=? WHERE session_id=?", row.get("turn_seq"), claim.sessionId());
            if ("CONTINUE".equals(decision.action())) {
                int previousJobs = jdbc().queryForObject("SELECT COUNT(*) FROM ai_ops_episode_consolidation_job WHERE episode_id=?", Integer.class, episode);
                if (!episode.equals(active) || previousJobs > 0) enqueue(episode, episode.equals(active) ? "NEW_REVISION" : "LATE_FEEDBACK", now);
            }
            return true;
        });
    }
    @Override public void fail(Claim claim, String reason, boolean unavailable, long now) {
        fail(claim, reason, unavailable, now, 0);
    }
    @Override public void fail(Claim claim, String reason, boolean unavailable, long now, long retryAfterMillis) {
        String status = unavailable ? "WAITING_MODEL" : claim.attempts() >= MAX_ATTEMPTS
                && !TRANSIENT_FAILURES.contains(reason) ? "RETRY_EXHAUSTED" : "PENDING_RETRY";
        jdbc().update("UPDATE " + table(claim.kind()) + " SET status=?,last_error=?,lease_until_ms=0,next_attempt_ms=? "
                + "WHERE id=? AND status='RUNNING' AND lease_owner=? AND epoch=? AND lease_until_ms>?",
                status, reason, now + Math.max(Math.min(86400000L, Math.max(0, retryAfterMillis)), unavailable ? 60000 : backoff(claim.attempts())), claim.id(), claim.owner(), claim.epoch(), now);
    }
    private void enqueue(String episode, String reason, long now) {
        var e = jdbc().queryForMap("SELECT * FROM ai_ops_task_episode WHERE episode_id=? FOR UPDATE", episode);
        var turns = jdbc().queryForList("SELECT turn_seq AS turnSeq,source_run_ref AS sourceRunRef,input_json,episode_revision AS episodeRevision "
                + "FROM ai_ops_task_episode_turn WHERE episode_id=? AND status='ASSIGNED' ORDER BY turn_seq", episode);
        List<Map<String, Object>> sources = turns.stream().map(t -> Map.<String, Object>of("turnSeq", t.get("turnSeq"),
                "sourceRunRef", t.get("sourceRunRef"), "episodeRevision", t.get("episodeRevision"), "source", object(t.get("input_json")))).toList();
        Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("episodeId", episode); snapshot.put("revision", e.get("revision"));
        snapshot.put("goal", e.get("goal")); snapshot.put("outcome", "UNKNOWN"); snapshot.put("artifactType", "PROGRESS");
        snapshot.put("recentlyActive", now - number(e.get("last_activity_ms")) < 1800000); snapshot.put("turns", sources);
        snapshot.put("sessionPendingTurns", jdbc().queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode_turn WHERE session_id=? AND status<>'ASSIGNED'",
                Integer.class, e.get("session_id")));
        String input = json(snapshot); boolean tooLarge = input.length() > 250000;
        jdbc().update("""
                INSERT INTO ai_ops_episode_consolidation_job(episode_id,revision,artifact_type,generator_hash,trigger_reasons,input_json,input_hash,status,last_error)
                VALUES (?,?,'PROGRESS',?,?,?,?,?,?) ON DUPLICATE KEY UPDATE
                trigger_reasons=IF(FIND_IN_SET(?,trigger_reasons)>0,trigger_reasons,CONCAT(trigger_reasons,',',?))
                """, episode, e.get("revision"), GENERATOR_HASH, reason, input, hash(input), tooLarge ? "SOURCE_BLOCKED" : "PENDING",
                tooLarge ? "EPISODE_INPUT_LIMIT" : "", reason, reason);
    }
    @Override public int sweep(String project, String reason, long now, int limit) {
        if (!Set.of("MIDNIGHT", "RECOVERY", "IDLE", "MANUAL").contains(reason)) throw new IllegalArgumentException("EPISODE_TRIGGER_INVALID");
        var candidates = jdbc().queryForList("""
                SELECT e.episode_id,e.session_id,e.project_id FROM ai_ops_task_episode e WHERE (?='' OR e.project_id=?)
                AND (?='MANUAL' OR (e.last_activity_ms<=? AND NOT EXISTS(SELECT 1 FROM ai_ops_chat_message m WHERE m.session_id=e.session_id AND m.create_time>?)))
                AND NOT EXISTS(SELECT 1 FROM ai_ops_episode_consolidation_job j WHERE j.episode_id=e.episode_id AND j.revision=e.revision
                  AND j.artifact_type='PROGRESS' AND j.generator_hash=?) ORDER BY e.last_activity_ms,e.episode_id LIMIT ?
                """, project, project, reason, now - 86400000L, new java.sql.Timestamp(now - 86400000L), GENERATOR_HASH, Math.max(1, Math.min(limit, 1000)));
        for (var e : candidates) tx(() -> {
            lockSession(text(e.get("session_id")), text(e.get("project_id")));
            // Recheck after acquiring the session lock: a newer reply may have arrived during the scan.
            if (!"MANUAL".equals(reason)) {
                long recent = jdbc().queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode e WHERE e.episode_id=? "
                        + "AND (e.last_activity_ms>? OR EXISTS(SELECT 1 FROM ai_ops_chat_message m WHERE m.session_id=e.session_id AND m.create_time>?))",
                        Long.class, e.get("episode_id"), now - 86400000L, new java.sql.Timestamp(now - 86400000L));
                if (recent > 0) return null;
            }
            enqueue(text(e.get("episode_id")), reason, now); return null;
        });
        return candidates.size();
    }
    @Override public List<Long> pendingConsolidations(long now, int limit) {
        return jdbc().queryForList("SELECT id FROM ai_ops_episode_consolidation_job WHERE status NOT IN ('SUCCEEDED','SOURCE_BLOCKED') AND " + retryEligibility("") + " "
                + "AND next_attempt_ms<=? AND lease_until_ms<=? ORDER BY id LIMIT ?", Long.class, now, now, limit);
    }
    @Override public Optional<Claim> claimConsolidation(long id, String owner, long now, boolean available) {
        return tx(() -> {
            var scope = jdbc().queryForList("SELECT e.session_id,e.project_id FROM ai_ops_task_episode e JOIN ai_ops_episode_consolidation_job j "
                    + "ON j.episode_id=e.episode_id WHERE j.id=?", id);
            if (scope.isEmpty()) return Optional.empty();
            String session = text(scope.get(0).get("session_id")); lockSession(session, text(scope.get(0).get("project_id")));
            var row = jdbc().queryForMap("SELECT * FROM ai_ops_episode_consolidation_job WHERE id=? FOR UPDATE", id);
            if (!eligible(row, now)) return Optional.empty();
            if (exhausted(row, "CONSOLIDATION", now)) return Optional.empty();
            if (!validInput(row, "CONSOLIDATION")) return Optional.empty();
            return Optional.of(claim(row, "CONSOLIDATION", session, owner, now, available));
        });
    }
    @Override public boolean complete(Claim claim, String content, long now) {
        if (!"CONSOLIDATION".equals(claim.kind())) throw new IllegalArgumentException("EPISODE_ARTIFACT_CLAIM_REQUIRED");
        return tx(() -> {
            var e = jdbc().queryForMap("SELECT project_id,session_id FROM ai_ops_task_episode WHERE episode_id=?", claim.episodeId());
            lockSession(text(e.get("session_id")), text(e.get("project_id")));
            var current = fenced(claim, now); if (current.isEmpty()) return false;
            var row = current.get();
            jdbc().update("INSERT INTO ai_ops_episode_artifact(episode_id,revision,artifact_type,generator_hash,content,content_hash,input_hash) "
                    + "VALUES (?,?,?,?,?,?,?)", claim.episodeId(), claim.revision(), row.get("artifact_type"), row.get("generator_hash"), content, hash(content), claim.inputHash());
            jdbc().update("UPDATE ai_ops_episode_consolidation_job SET status='SUCCEEDED',lease_until_ms=0,last_error='' WHERE id=?", claim.id());
            // A late artifact is retained for audit but cannot advance the head of a newer revision.
            jdbc().update("UPDATE ai_ops_task_episode SET last_consolidated_revision=? WHERE episode_id=? AND revision=? AND last_consolidated_revision<?",
                    claim.revision(), claim.episodeId(), claim.revision(), claim.revision());
            return true;
        });
    }
    @Override public Map<String, Object> view(String project, String session, String user, boolean admin, int limit) {
        int bound = Math.max(1, Math.min(limit, 100));
        if (session == null || session.isBlank()) throw new IllegalArgumentException("EPISODE_SESSION_REQUIRED");
        int authorized = jdbc().queryForObject("SELECT COUNT(*) FROM ai_ops_chat_session WHERE session_id=? AND project_id=? AND (?=TRUE OR user_id=?)",
                Integer.class, session, project, admin, user);
        if (authorized == 0) throw new SecurityException("EPISODE_SESSION_ACCESS_DENIED");
        var episodes = jdbc().queryForList("SELECT episode_id AS episodeId,goal,revision,outcome,verified_outcome_ref AS verifiedOutcomeRef,"
                + "last_consolidated_revision AS lastConsolidatedRevision,last_activity_ms AS lastActivityMs FROM ai_ops_task_episode "
                + "WHERE project_id=? AND session_id=? ORDER BY create_time,episode_id LIMIT ?", project, session, bound);
        var turns = jdbc().queryForList("SELECT turn_seq AS turnSeq,end_seq AS endSeq,source_run_ref AS sourceRunRef,episode_id AS episodeId,"
                + "episode_revision AS episodeRevision,status,attempts,epoch,input_hash AS inputHash,context_fidelity AS contextFidelity,"
                + "classifier_revision AS classifierRevision,last_error AS lastError FROM ai_ops_task_episode_turn "
                + "WHERE project_id=? AND session_id=? ORDER BY turn_seq LIMIT ?", project, session, bound);
        boolean earlierPending = false;
        for (var turn : turns) {
            turn.put("blockedByPrevious", earlierPending && !"ASSIGNED".equals(turn.get("status")));
            if (!"ASSIGNED".equals(turn.get("status"))) earlierPending = true;
        }
        var jobs = jdbc().queryForList("SELECT j.id,j.episode_id AS episodeId,j.revision,j.artifact_type AS artifactType,j.status,j.attempts,j.epoch,"
                + "j.generator_hash AS generatorHash,j.trigger_reasons AS triggerReasons,j.last_error AS lastError FROM ai_ops_episode_consolidation_job j "
                + "JOIN ai_ops_task_episode e ON e.episode_id=j.episode_id WHERE e.project_id=? AND e.session_id=? ORDER BY j.id DESC LIMIT ?", project, session, bound);
        var artifacts = jdbc().queryForList("SELECT a.episode_id AS episodeId,a.revision,a.artifact_type AS artifactType,a.content,a.content_hash AS contentHash,a.outcome "
                + "FROM ai_ops_episode_artifact a JOIN ai_ops_task_episode e ON e.episode_id=a.episode_id WHERE e.project_id=? AND e.session_id=? "
                + "ORDER BY a.create_time DESC LIMIT ?", project, session, bound);
        return Map.of("episodes", episodes, "turns", turns, "jobs", jobs, "artifacts", artifacts, "model", TaskEpisodeModelPort.MODEL);
    }
}
