package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** A unique scope key makes concurrent starts and process recovery observe the same selection. */
@Repository
public class JdbcMcpDiscoverySelectionStore implements McpDiscoverySelectionStore {
    private final JdbcTemplate jdbc;
    public JdbcMcpDiscoverySelectionStore(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Selection freeze(Scope scope, Selection proposed) {
        String key = CanonicalObjectHasher.sha256(scope);
        jdbc.update("""
                INSERT INTO ai_ops_mcp_discovery_selection
                (scope_hash,project_id,run_id,agent_id,node_id,discovery_mode,tool_count,summary_tokens,tokenizer,catalog_hash,counting_basis_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE scope_hash=VALUES(scope_hash)
                """, key, scope.projectId(), scope.runId(), scope.agentId(), scope.nodeId(), proposed.mode(),
                proposed.toolCount(), proposed.summaryTokens(), proposed.tokenizer(), proposed.catalogHash(),
                proposed.countingBasis() == null ? null : com.alibaba.fastjson.JSON.toJSONString(proposed.countingBasis()));
        return jdbc.queryForObject("""
                SELECT discovery_mode,tool_count,summary_tokens,tokenizer,catalog_hash,counting_basis_json FROM ai_ops_mcp_discovery_selection
                WHERE scope_hash=? AND project_id=? AND run_id=? AND agent_id=? AND node_id=?
                """, (r, n) -> new Selection(r.getString(1), r.getInt(2), r.getInt(3), r.getString(4), r.getString(5),
                        com.alibaba.fastjson.JSON.parseObject(r.getString(6), CountingBasis.class)),
                key, scope.projectId(), scope.runId(), scope.agentId(), scope.nodeId());
    }
}
