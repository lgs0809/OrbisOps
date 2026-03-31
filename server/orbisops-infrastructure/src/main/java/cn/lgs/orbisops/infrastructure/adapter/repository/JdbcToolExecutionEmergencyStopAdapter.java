package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionAuditPort;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionEmergencyStopPort;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable project-scoped emergency stop adapter; unavailable state fails closed for writes. */
@Component
public final class JdbcToolExecutionEmergencyStopAdapter
        implements ToolExecutionEmergencyStopPort {

    private final JdbcTemplate jdbc;
    private final ToolExecutionAuditPort audit;
    private final boolean globalStop;
    private final boolean autoInit;
    private final Map<String, StopState> states = new ConcurrentHashMap<>();
    private final AtomicBoolean stateAvailable = new AtomicBoolean(false);

    public JdbcToolExecutionEmergencyStopAdapter(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            ToolExecutionAuditPort audit,
            @Value("${orbisops.tool-execution.emergency-stop:false}") boolean globalStop,
            @Value("${orbisops.tool-execution.emergency-stop-auto-init:true}") boolean autoInit) {
        this.jdbc = jdbcProvider == null ? null : jdbcProvider.getIfAvailable();
        if (audit == null) throw new IllegalArgumentException("TOOL_EXECUTION_EMERGENCY_STOP_AUDIT_REQUIRED");
        this.audit = audit;
        this.globalStop = globalStop;
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (jdbc == null) return;
        try {
            if (autoInit) {
                jdbc.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_tool_execution_emergency_stop (
                          project_id VARCHAR(128) NOT NULL,
                          active TINYINT NOT NULL DEFAULT 0,
                          reason VARCHAR(500) NOT NULL DEFAULT '',
                          actor VARCHAR(128) NOT NULL DEFAULT '',
                          updated_at DATETIME(6) NOT NULL,
                          PRIMARY KEY (project_id),
                          KEY idx_tool_emergency_stop_active (active, updated_at)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                          COMMENT='ToolExecution project emergency stop facts'
                        """);
            }
            jdbc.query("""
                            SELECT project_id, active, reason, actor, updated_at
                              FROM ai_ops_tool_execution_emergency_stop
                            """,
                    (RowCallbackHandler) rs -> states.put(rs.getString("project_id"), new StopState(
                            rs.getBoolean("active"),
                            rs.getString("reason"),
                            rs.getString("actor"),
                            rs.getTimestamp("updated_at").toInstant())));
            stateAvailable.set(true);
        } catch (RuntimeException error) {
            stateAvailable.set(false);
        }
    }

    @Override
    public boolean blocks(ToolExecutionRequest request, ToolExecutionTarget target) {
        if (target == null || target.readOnly()) return false;
        if (globalStop || !stateAvailable.get()) return true;
        StopState state = states.get(text(request == null ? null : request.projectId()));
        return state != null && state.active();
    }

    @Override
    public StopState set(String projectId, boolean active, String reason, String actor) {
        String project = required(projectId, "PROJECT_ID_REQUIRED");
        String stopReason = required(reason, "EMERGENCY_STOP_REASON_REQUIRED");
        String stopActor = required(actor, "EMERGENCY_STOP_ACTOR_REQUIRED");
        if (jdbc == null || !stateAvailable.get()) {
            throw new IllegalStateException("TOOL_EXECUTION_EMERGENCY_STOP_STORE_UNAVAILABLE");
        }
        Instant updatedAt = Instant.now();
        jdbc.update("""
                        INSERT INTO ai_ops_tool_execution_emergency_stop
                          (project_id, active, reason, actor, updated_at)
                        VALUES (?,?,?,?,?)
                        ON DUPLICATE KEY UPDATE active=VALUES(active), reason=VALUES(reason),
                          actor=VALUES(actor), updated_at=VALUES(updated_at)
                        """,
                project, active ? 1 : 0, stopReason, stopActor, Timestamp.from(updatedAt));
        StopState state = new StopState(active, stopReason, stopActor, updatedAt);
        states.put(project, state);
        audit.record(new ToolExecutionAuditPort.ToolExecutionAuditEvent(
                project,
                active ? "tool_execution_emergency_stop_activated" : "tool_execution_emergency_stop_released",
                project,
                Map.of(
                        "active", active,
                        "reason", stopReason,
                        "actor", stopActor,
                        "updatedAt", updatedAt.toString())));
        return state;
    }

    @Override
    public StopStatus status(String projectId) {
        String project = required(projectId, "PROJECT_ID_REQUIRED");
        StopState state = states.get(project);
        return new StopStatus(
                project,
                globalStop || !stateAvailable.get() || state != null && state.active(),
                globalStop,
                stateAvailable.get(),
                state == null ? "" : state.reason(),
                state == null ? "" : state.actor(),
                state == null ? "" : state.updatedAt().toString());
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
