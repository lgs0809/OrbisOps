package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Reads retained source facts only. Classifier output is not a source of normal memory. */
final class JdbcTaskEpisodeSource {
    private final JdbcTemplate jdbc;
    JdbcTaskEpisodeSource(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<Map<String, Object>> freeze(Map<String, Object> turn, String activeEpisode) {
        String project = text(turn.get("project_id")), session = text(turn.get("session_id")), run = text(turn.get("source_run_ref"));
        var runs = jdbc.queryForList("SELECT status FROM ai_ops_agent_run WHERE run_id=? AND project_id=? AND session_id=?", run, project, session);
        if (runs.isEmpty() || !Set.of("SUCCEEDED", "FAILED", "CANCELED").contains(text(runs.get(0).get("status")))) return Optional.empty();
        var messages = jdbc.queryForList("""
                SELECT message_seq AS messageSeq, role, content, metadata
                FROM ai_ops_chat_message WHERE project_id=? AND session_id=? AND turn_id=?
                ORDER BY message_seq
                """, project, session, run);
        boolean replied = messages.stream().anyMatch(m -> "assistant".equals(text(m.get("role")))
                && !"WAITING_APPROVAL".equals(text(object(m.get("metadata")).get("status"))));
        if (!replied) return Optional.empty();
        int inFlight = jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_tool_result WHERE project_id=? AND run_id=? "
                + "AND status IN ('PENDING','RUNNING','STARTED','DISPATCHING')", Integer.class, project, run);
        if (inFlight > 0) return Optional.empty();
        long end = messages.stream().mapToLong(m -> number(m.get("messageSeq"))).max().orElseThrow();
        // Do not copy arbitrary metadata (for example auth principals) into the classifier.
        List<Map<String, Object>> safeMessages = messages.stream().map(m -> Map.<String, Object>of(
                "messageSeq", m.get("messageSeq"), "role", text(m.get("role")), "content", text(m.get("content")),
                "status", text(object(m.get("metadata")).get("status")))).toList();
        var context = jdbc.queryForList("SELECT snapshot_json, snapshot_hash FROM ai_ops_task_episode_context "
                + "WHERE run_id=? AND project_id=? AND session_id=?", run, project, session);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", project); input.put("sessionId", session); input.put("sourceRunRef", run);
        input.put("turnSeq", turn.get("turn_seq")); input.put("endSeq", end);
        input.put("activeEpisodeId", activeEpisode); input.put("turnMessages", safeMessages);
        input.put("runStatus", runs.get(0).get("status"));
        input.put("sourceLastActivityMs", jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(MAX(create_time))*1000 AS SIGNED) "
                + "FROM ai_ops_chat_message WHERE project_id=? AND session_id=? AND turn_id=?", Long.class, project, session, run));
        if (!context.isEmpty()) {
            String snapshot = text(context.get(0).get("snapshot_json"));
            if (!hash(snapshot).equals(text(context.get(0).get("snapshot_hash"))))
                throw new IllegalStateException("EPISODE_CONTEXT_HASH_MISMATCH");
            input.put("originalContext", object(snapshot));
            input.put("contextFidelity", "FROZEN_MAIN_CONTEXT");
        } else {
            // Historical runs predate capture. Explicitly identify reconstruction instead of claiming exact replay.
            var history = jdbc.queryForList("""
                    SELECT message_seq AS messageSeq, role, content FROM ai_ops_chat_message
                    WHERE project_id=? AND session_id=? AND message_seq<? ORDER BY message_seq DESC LIMIT 128
                    """, project, session, turn.get("turn_seq"));
            Collections.reverse(history);
            var bundles = jdbc.queryForList("SELECT bundle_id AS bundleId, bundle_hash AS bundleHash, bundle_json "
                    + "FROM ai_ops_runtime_context_bundle WHERE run_id=? AND project_id=? AND session_id=? ORDER BY id LIMIT 1", run, project, session);
            Map<String, Object> rebuilt = new LinkedHashMap<>(); rebuilt.put("retainedMessages", history);
            if (!bundles.isEmpty()) {
                var bundle = bundles.get(0); var data = object(bundle.get("bundle_json"));
                rebuilt.put("bundleId", bundle.get("bundleId")); rebuilt.put("bundleHash", bundle.get("bundleHash"));
                rebuilt.put("compressedMemorySummary", text(data.get("compressedMemorySummary")));
            }
            input.put("originalContext", rebuilt); input.put("contextFidelity", "RECONSTRUCTED_RETAINED_CONTEXT");
        }
        var tools = jdbc.queryForList("SELECT result_id AS resultId, tool_name AS toolName, status, output_hash AS outputHash "
                + "FROM ai_ops_tool_result WHERE project_id=? AND run_id=? ORDER BY id LIMIT 201", project, run);
        if (tools.size() > 200) throw new IllegalStateException("EPISODE_INPUT_LIMIT");
        input.put("toolResultRefs", tools);
        var episodes = jdbc.queryForList("SELECT episode_id AS episodeId, goal, revision, outcome FROM ai_ops_task_episode "
                + "WHERE project_id=? AND session_id=? ORDER BY create_time, episode_id LIMIT 201", project, session);
        if (episodes.size() > 200) throw new IllegalStateException("EPISODE_CATALOG_LIMIT");
        input.put("knownEpisodes", episodes);
        if (json(input).length() > 120000) throw new IllegalStateException("EPISODE_INPUT_LIMIT");
        return Optional.of(input);
    }
}
