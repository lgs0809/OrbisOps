package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.model.McpCatalogSummary;
import cn.lgs.orbisops.domain.mcp.model.McpRoutingDecision;
import cn.lgs.orbisops.domain.mcp.model.McpRuntimeActivation;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaCacheEntry;
import cn.lgs.orbisops.domain.mcp.model.McpToolCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMcpRuntimeCatalogRepository implements IMcpRuntimeCatalogRepository {

    private static final String ACTIVATION_COLUMNS = """
            activation_id, project_id, run_id, session_id, agent_id, mcp_id, tool_name, schema_hash,
            disclosure_tier, CASE WHEN status='ACTIVE' AND expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE status END AS status,
            expires_at, metadata_json, create_time, update_time
            """;
    private static final String DECISION_COLUMNS = """
            decision_id, project_id, agent_id, node_id, run_id, capability, request_json,
            selected_tools_json, reason, status, create_time
            """;
    private static final String CALL_COLUMNS = """
            call_id, project_id, agent_id, node_id, run_id, tool_id, mcp_id, tool_name, risk_level,
            read_only, status, input_json, output_json, duration_ms, error_message, create_time
            """;

    @Qualifier("mysqlJdbcTemplate")
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public void saveCatalogSummary(McpCatalogSummary summary) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_tool_catalog_summary (project_id, summary_json, tool_count)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE summary_json=VALUES(summary_json), tool_count=VALUES(tool_count)
                """, summary.projectId(), summary.summaryJson(), summary.toolCount());
    }

    @Override
    public void saveSchemaCache(McpSchemaCacheEntry schema) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_tool_schema_cache (cache_id, project_id, tool_id, schema_json, status)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE schema_json=VALUES(schema_json), status=VALUES(status)
                """, schema.cacheId(), schema.projectId(), schema.toolId(), schema.schemaJson(), schema.status());
    }

    @Override
    public void saveActivation(McpRuntimeActivation activation) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_mcp_tool_activation
                  (activation_id, project_id, run_id, session_id, agent_id, mcp_id, tool_name, schema_hash,
                   disclosure_tier, status, expires_at, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE schema_hash=VALUES(schema_hash), disclosure_tier=VALUES(disclosure_tier),
                  status=VALUES(status), expires_at=VALUES(expires_at), metadata_json=VALUES(metadata_json)
                """, activation.activationId(), activation.projectId(), activation.runId(), activation.sessionId(),
                activation.agentId(), activation.mcpId(), activation.toolName(), activation.schemaHash(),
                activation.disclosureTier(), activation.status(), timestamp(activation.expiresAt()),
                activation.metadataJson());
    }

    @Override
    public boolean isActivationActive(String projectId, String runId, String mcpId, String toolName) {
        requireAvailable();
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM ai_ops_mcp_tool_activation
                WHERE project_id=? AND run_id=? AND mcp_id=? AND tool_name=?
                  AND status='ACTIVE' AND expires_at>CURRENT_TIMESTAMP
                """, Integer.class, projectId, runId, mcpId, toolName);
        return count != null && count > 0;
    }

    @Override
    public List<McpRuntimeActivation> findActivations(String projectId, int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + ACTIVATION_COLUMNS + " FROM ai_ops_mcp_tool_activation "
                        + "WHERE project_id=? ORDER BY id DESC LIMIT ?",
                activationMapper(), projectId, bounded(limit));
    }

    @Override
    public void saveRoutingDecision(McpRoutingDecision decision) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_tool_routing_decision
                  (decision_id, project_id, agent_id, node_id, run_id, capability, request_json,
                   selected_tools_json, reason, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, decision.decisionId(), decision.projectId(), decision.agentId(), decision.nodeId(),
                decision.runId(), decision.capability(), decision.requestJson(), decision.selectedToolsJson(),
                decision.reason(), decision.status());
    }

    @Override
    public List<McpRoutingDecision> findRoutingDecisions(String projectId, int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + DECISION_COLUMNS + " FROM ai_ops_tool_routing_decision "
                        + "WHERE project_id=? ORDER BY id DESC LIMIT ?",
                decisionMapper(), projectId, bounded(limit));
    }

    @Override
    public void saveToolCall(McpToolCall call) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_mcp_tool_call
                  (call_id, project_id, agent_id, node_id, run_id, tool_id, mcp_id, tool_name, risk_level,
                   read_only, status, input_json, output_json, duration_ms, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, call.callId(), call.projectId(), call.agentId(), call.nodeId(), call.runId(), call.toolId(),
                call.mcpId(), call.toolName(), call.riskLevel(), call.readOnly() ? 1 : 0, call.status(),
                call.inputJson(), call.outputJson(), call.durationMs(), call.errorMessage());
    }

    @Override
    public List<McpToolCall> findToolCalls(String projectId, int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + CALL_COLUMNS + " FROM ai_ops_mcp_tool_call "
                        + "WHERE project_id=? ORDER BY id DESC LIMIT ?",
                toolCallMapper(), projectId, bounded(limit));
    }

    @Override
    public Optional<McpToolCall> findToolCall(String projectId, String callId) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + CALL_COLUMNS + " FROM ai_ops_mcp_tool_call "
                        + "WHERE project_id=? AND call_id=? LIMIT 1",
                toolCallMapper(), projectId, callId).stream().findFirst();
    }

    private RowMapper<McpRuntimeActivation> activationMapper() {
        return (rs, rowNum) -> new McpRuntimeActivation(
                rs.getString("activation_id"), rs.getString("project_id"), rs.getString("run_id"),
                rs.getString("session_id"), rs.getString("agent_id"), rs.getString("mcp_id"),
                rs.getString("tool_name"), rs.getString("schema_hash"), rs.getString("disclosure_tier"),
                rs.getString("status"), localDateTime(rs.getTimestamp("expires_at")),
                rs.getString("metadata_json"), localDateTime(rs.getTimestamp("create_time")),
                localDateTime(rs.getTimestamp("update_time")));
    }

    private RowMapper<McpRoutingDecision> decisionMapper() {
        return (rs, rowNum) -> new McpRoutingDecision(
                rs.getString("decision_id"), rs.getString("project_id"), rs.getString("agent_id"),
                rs.getString("node_id"), rs.getString("run_id"), rs.getString("capability"),
                rs.getString("request_json"), rs.getString("selected_tools_json"), rs.getString("reason"),
                rs.getString("status"), localDateTime(rs.getTimestamp("create_time")));
    }

    private RowMapper<McpToolCall> toolCallMapper() {
        return (rs, rowNum) -> new McpToolCall(
                rs.getString("call_id"), rs.getString("project_id"), rs.getString("agent_id"),
                rs.getString("node_id"), rs.getString("run_id"), rs.getString("tool_id"),
                rs.getString("mcp_id"), rs.getString("tool_name"), rs.getString("risk_level"),
                rs.getInt("read_only") == 1, rs.getString("status"), rs.getString("input_json"),
                rs.getString("output_json"), nullableLong(rs.getObject("duration_ms")),
                rs.getString("error_message"), localDateTime(rs.getTimestamp("create_time")));
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE");
    }

    private int bounded(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
