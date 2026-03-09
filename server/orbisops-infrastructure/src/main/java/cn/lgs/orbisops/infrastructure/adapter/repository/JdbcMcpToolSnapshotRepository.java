package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolSnapshotRepository;
import cn.lgs.orbisops.domain.mcp.model.McpToolSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMcpToolSnapshotRepository implements IMcpToolSnapshotRepository {

    private static final String SELECT_COLUMNS = """
            snapshot_id, project_id, mcp_id, tool_id, tool_name, schema_hash, schema_json,
            metadata_json, metadata_complete, status, create_time, update_time
            """;

    @Qualifier("mysqlJdbcTemplate")
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public void save(McpToolSnapshot snapshot) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_mcp_tool_snapshot
                  (snapshot_id, project_id, mcp_id, tool_id, tool_name, schema_hash, schema_json,
                   metadata_json, metadata_complete, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  schema_json=VALUES(schema_json), metadata_json=VALUES(metadata_json),
                  metadata_complete=VALUES(metadata_complete), status=VALUES(status)
                """, snapshot.snapshotId(), snapshot.projectId(), snapshot.mcpId(), snapshot.toolId(),
                snapshot.toolName(), snapshot.schemaHash(), snapshot.schemaJson(), snapshot.metadataJson(),
                snapshot.metadataComplete() ? 1 : 0, snapshot.status());
    }

    @Override
    public List<McpToolSnapshot> findAll(String projectId, int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_snapshot "
                        + "WHERE project_id=? ORDER BY id DESC LIMIT ?",
                mapper(), projectId, bounded(limit));
    }

    @Override
    public List<McpToolSnapshot> findRecentActive(String projectId, String mcpId, String toolName, int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_snapshot "
                        + "WHERE project_id=? AND mcp_id=? AND tool_name=? AND status='ACTIVE' "
                        + "ORDER BY id DESC LIMIT ?",
                mapper(), projectId, mcpId, toolName, bounded(limit));
    }

    @Override
    public Optional<McpToolSnapshot> findLatestActive(String projectId,
                                                      String mcpId,
                                                      String toolId,
                                                      String toolName) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_snapshot "
                        + "WHERE project_id=? AND (mcp_id=? OR tool_id=?) AND tool_name=? AND status='ACTIVE' "
                        + "ORDER BY id DESC LIMIT 1",
                mapper(), projectId, mcpId, toolId, toolName).stream().findFirst();
    }

    private RowMapper<McpToolSnapshot> mapper() {
        return (rs, rowNum) -> new McpToolSnapshot(
                rs.getString("snapshot_id"), rs.getString("project_id"), rs.getString("mcp_id"),
                rs.getString("tool_id"), rs.getString("tool_name"), rs.getString("schema_hash"),
                rs.getString("schema_json"), rs.getString("metadata_json"),
                rs.getInt("metadata_complete") == 1, rs.getString("status"),
                localDateTime(rs.getTimestamp("create_time")), localDateTime(rs.getTimestamp("update_time")));
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("MCP_TOOL_SNAPSHOT_STORE_UNAVAILABLE");
    }

    private int bounded(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private java.time.LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
