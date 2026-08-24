package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.VerifiedTaskOutcome;
import org.springframework.jdbc.core.JdbcTemplate;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Rechecks the current acceptance and retained receipts, including unassigned corrections. */
final class JdbcVerifiedTaskOutcomeReader {
    private final JdbcTemplate jdbc;
    JdbcVerifiedTaskOutcomeReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    VerifiedTaskOutcome read(String project, String run) {
        var rows = jdbc.queryForList("""
                SELECT e.episode_id,e.session_id,e.revision,a.acceptance_id,a.condition_key,a.record_json,a.record_hash
                FROM ai_ops_task_episode_turn t JOIN ai_ops_task_episode e ON e.episode_id=t.episode_id AND e.project_id=t.project_id
                JOIN ai_ops_task_acceptance a ON a.acceptance_id=e.verified_outcome_ref AND a.project_id=e.project_id
                    AND a.episode_id=e.episode_id AND a.episode_revision=e.revision AND a.source_run_id=t.source_run_ref
                WHERE t.project_id=? AND t.source_run_ref=? AND t.status='ASSIGNED' AND t.episode_revision=e.revision
                    AND e.outcome='SUCCEEDED' AND a.outcome='SUCCEEDED'
                """,project,run);
        if (rows.size()!=1) return VerifiedTaskOutcome.unknown();
        var row=rows.get(0);
        var runRows=jdbc.queryForList("SELECT * FROM ai_ops_agent_run WHERE run_id=? AND project_id=?",run,project);
        if(runRows.size()!=1 || "SUB_WORKFLOW".equals(text(object(object(runRows.get(0).get("request_json")).get("metadata")).get("source"))))
            return VerifiedTaskOutcome.unknown(); // A delegated execution cannot become another independent task source.

        if (!ready(project,text(row.get("episode_id")),text(row.get("session_id"))) || !new JdbcTaskAcceptanceIntegrity(jdbc).valid(project,text(row.get("episode_id")),number(row.get("revision")),
                run,text(row.get("acceptance_id")),text(row.get("condition_key")),text(row.get("record_json")),text(row.get("record_hash"))))
            return VerifiedTaskOutcome.unknown();
        return new VerifiedTaskOutcome(project,run,text(row.get("episode_id")),number(row.get("revision")),
                text(row.get("acceptance_id")),text(row.get("condition_key")));
    }
    boolean ready(String project,String episode,String session) {
        long pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_ops_chat_message m
                LEFT JOIN ai_ops_task_episode_turn t ON t.session_id=m.session_id AND t.source_run_ref=m.turn_id
                WHERE m.project_id=? AND m.session_id=? AND m.role='user' AND (t.id IS NULL OR t.status<>'ASSIGNED')
                """,Long.class,project,session);
        long unfinished = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_ops_task_episode_turn t LEFT JOIN ai_ops_agent_run r ON r.run_id=t.source_run_ref
                WHERE t.project_id=? AND t.episode_id=? AND (r.run_id IS NULL OR r.project_id<>t.project_id
                  OR r.session_id<>t.session_id OR r.status NOT IN ('SUCCEEDED','FAILED','CANCELED'))
                """,Long.class,project,episode);
        var scope=new JdbcTaskEpisodeRunScope(jdbc);
        try {
            boolean childrenReady=scope.runs(project,episode).stream().allMatch(r->java.util.Set.of("SUCCEEDED","FAILED","CANCELED").contains(text(r.row().get("status"))));
            boolean activeTools=scope.receipts(project,episode).stream().anyMatch(r->java.util.Set.of("PENDING","STARTED","RUNNING","DISPATCHING","UNKNOWN","WAITING_APPROVAL").contains(text(r.get("status"))));
            return pending==0 && unfinished==0 && childrenReady && !activeTools;
        } catch(SecurityException | IllegalArgumentException invalid) { return false; }
    }
}
