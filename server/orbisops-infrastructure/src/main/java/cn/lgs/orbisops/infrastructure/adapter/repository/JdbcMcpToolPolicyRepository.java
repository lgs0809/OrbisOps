package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyAdminRecord;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMcpToolPolicyRepository implements IMcpToolPolicyRepository {

    private static final String SELECT_COLUMNS = """
            p.id, p.policy_id, p.project_id, p.mcp_id, p.tool_id, p.tool_name, p.schema_hash,
            p.effect_type, p.effect_scope, p.mutability, p.capability, p.allowed_actions_json,
            p.risk_level, p.read_only, p.investigate_allowed, p.prepare_allowed, p.land_allowed,
            p.requires_approved_package, p.requires_human_approval, p.requires_dry_run,
            p.requires_rollback_plan, p.argument_policy_json, p.status,
            p.review_status, p.reviewed_by, p.reviewed_at, p.suggested_by, p.suggested_at,
            p.metadata_json, p.create_time, p.update_time
            """;

    @Qualifier("mysqlJdbcTemplate")
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public boolean available() {
        return jdbcTemplate != null;
    }

    @Override
    public List<McpToolPolicy> findAll(String projectId, int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_policy p "
                        + "WHERE p.project_id=? AND p.schema_hash<>'' ORDER BY p.id DESC LIMIT ?",
                mapper(), projectId, bounded(limit));
    }

    @Override
    public List<McpToolPolicyAdminRecord> findAllForAdmin(String projectId, int limit) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_policy p "
                        + "WHERE p.project_id=? ORDER BY p.id DESC LIMIT ?",
                adminMapper(), projectId, bounded(limit));
    }

    @Override
    public List<McpToolPolicy> findRuntimeExecutable(String projectId, String toolIdOrMcpId) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_policy p "
                        + "WHERE p.project_id=? AND (p.mcp_id=? OR p.tool_id=?) "
                        + "AND p.status='ACTIVE' AND p.review_status IN ('HUMAN_REVIEWED','SYSTEM_VERIFIED') "
                        + "AND p.schema_hash<>'' "
                        + "AND p.schema_hash=(SELECT s.schema_hash FROM ai_ops_mcp_tool_snapshot s "
                        + "WHERE s.project_id=p.project_id AND (s.mcp_id=p.mcp_id OR s.tool_id=p.tool_id) "
                        + "AND s.tool_name=p.tool_name AND s.status='ACTIVE' ORDER BY s.id DESC LIMIT 1) "
                        + "ORDER BY p.id DESC",
                mapper(), projectId, toolIdOrMcpId, toolIdOrMcpId);
    }

    @Override
    public Optional<McpToolPolicy> findById(String projectId, String policyId) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_policy p "
                        + "WHERE p.project_id=? AND p.policy_id=? LIMIT 1",
                mapper(), projectId, policyId).stream().findFirst();
    }

    @Override
    public Optional<McpToolPolicy> findActiveReviewed(String projectId,
                                                      String mcpId,
                                                      String toolName,
                                                      String schemaHash) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_policy p "
                        + "WHERE p.project_id=? AND p.mcp_id=? AND p.tool_name=? "
                        + "AND p.status='ACTIVE' AND p.review_status IN ('HUMAN_REVIEWED','SYSTEM_VERIFIED') "
                        + "AND p.schema_hash<>'' AND p.schema_hash=? "
                        + "ORDER BY p.id DESC LIMIT 1",
                mapper(), projectId, mcpId, toolName, schemaHash).stream().findFirst();
    }

    @Override
    public Optional<McpToolPolicy> findPendingSuggestion(String projectId,
                                                         String mcpId,
                                                         String toolName,
                                                         String schemaHash) {
        requireAvailable();
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM ai_ops_mcp_tool_policy p "
                        + "WHERE p.project_id=? AND p.mcp_id=? AND p.tool_name=? "
                        + "AND p.status='PENDING_REVIEW' AND p.review_status<>'HUMAN_REVIEWED' "
                        + "AND p.schema_hash<>'' AND p.schema_hash=? "
                        + "ORDER BY p.id ASC LIMIT 1",
                mapper(), projectId, mcpId, toolName, schemaHash).stream().findFirst();
    }

    @Override
    public void save(McpToolPolicy policy) {
        requireAvailable();
        jdbcTemplate.update("""
                INSERT INTO ai_ops_mcp_tool_policy
                  (policy_id, project_id, mcp_id, tool_id, tool_name, schema_hash, effect_type, effect_scope,
                   mutability, capability, allowed_actions_json, risk_level, read_only, investigate_allowed,
                   prepare_allowed, land_allowed, requires_approved_package, requires_human_approval,
                   requires_dry_run, requires_rollback_plan, argument_policy_json,
                   status, review_status, reviewed_by, reviewed_at, suggested_by, suggested_at, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  schema_hash=VALUES(schema_hash), effect_type=VALUES(effect_type), effect_scope=VALUES(effect_scope),
                  mutability=VALUES(mutability), capability=VALUES(capability),
                  allowed_actions_json=VALUES(allowed_actions_json), risk_level=VALUES(risk_level),
                  read_only=VALUES(read_only), investigate_allowed=VALUES(investigate_allowed),
                  prepare_allowed=VALUES(prepare_allowed), land_allowed=VALUES(land_allowed),
                  requires_approved_package=VALUES(requires_approved_package),
                  requires_human_approval=VALUES(requires_human_approval), requires_dry_run=VALUES(requires_dry_run),
                  requires_rollback_plan=VALUES(requires_rollback_plan),
                  argument_policy_json=VALUES(argument_policy_json), status=VALUES(status),
                  review_status=VALUES(review_status), reviewed_by=VALUES(reviewed_by),
                  reviewed_at=VALUES(reviewed_at), suggested_by=VALUES(suggested_by),
                  suggested_at=VALUES(suggested_at), metadata_json=VALUES(metadata_json)
                """, policy.policyId(), policy.projectId(), policy.mcpId(), policy.toolId(), policy.toolName(),
                policy.schemaHash(), policy.effectType(), policy.effectScope(), policy.mutability(), policy.capability(),
                policy.allowedActionsJson(), policy.riskLevel().name(), flag(policy.readOnly()), flag(policy.investigateAllowed()),
                flag(policy.prepareAllowed()), flag(policy.landAllowed()), flag(policy.requiresApprovedPackage()),
                flag(policy.requiresHumanApproval()), flag(policy.requiresDryRun()),
                flag(policy.requiresRollbackPlan()), policy.argumentPolicyJson(), policy.status().name(), policy.reviewStatus().name(),
                policy.reviewedBy(), timestamp(policy.reviewedAt()), policy.suggestedBy(),
                timestamp(policy.suggestedAt()), policy.metadataJson());
    }

    @Override
    public boolean saveSuggestionIfAbsent(McpToolPolicy policy) {
        requireAvailable();
        return jdbcTemplate.update("""
                INSERT IGNORE INTO ai_ops_mcp_tool_policy
                  (policy_id, project_id, mcp_id, tool_id, tool_name, schema_hash, effect_type, effect_scope,
                   mutability, capability, allowed_actions_json, risk_level, read_only, investigate_allowed,
                   prepare_allowed, land_allowed, requires_approved_package, requires_human_approval,
                   requires_dry_run, requires_rollback_plan, argument_policy_json,
                   status, review_status, reviewed_by, reviewed_at, suggested_by, suggested_at, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, policy.policyId(), policy.projectId(), policy.mcpId(), policy.toolId(), policy.toolName(),
                policy.schemaHash(), policy.effectType(), policy.effectScope(), policy.mutability(), policy.capability(),
                policy.allowedActionsJson(), policy.riskLevel().name(), flag(policy.readOnly()), flag(policy.investigateAllowed()),
                flag(policy.prepareAllowed()), flag(policy.landAllowed()), flag(policy.requiresApprovedPackage()),
                flag(policy.requiresHumanApproval()), flag(policy.requiresDryRun()),
                flag(policy.requiresRollbackPlan()), policy.argumentPolicyJson(), policy.status().name(), policy.reviewStatus().name(),
                policy.reviewedBy(), timestamp(policy.reviewedAt()), policy.suggestedBy(),
                timestamp(policy.suggestedAt()), policy.metadataJson()) == 1;
    }

    @Override
    public boolean updateReviewStatus(String projectId,
                                      String policyId,
                                      McpToolPolicyStatus status,
                                      McpToolPolicyReviewStatus reviewStatus,
                                      String actor,
                                      String metadataJson) {
        requireAvailable();
        return jdbcTemplate.update("""
                UPDATE ai_ops_mcp_tool_policy
                SET status=?, review_status=?, reviewed_by=?,
                    reviewed_at=CURRENT_TIMESTAMP, metadata_json=?
                WHERE project_id=? AND policy_id=?
                """, status.name(), reviewStatus.name(), actor, metadataJson, projectId, policyId) == 1;
    }

    @Override
    public int markActivePoliciesStale(String projectId, String mcpId, String toolName, String newSchemaHash) {
        requireAvailable();
        return jdbcTemplate.update("""
                UPDATE ai_ops_mcp_tool_policy
                SET status='STALE',
                    metadata_json=JSON_SET(
                      CASE WHEN JSON_VALID(metadata_json) THEN COALESCE(metadata_json, '{}') ELSE '{}' END,
                      '$.staleReason', 'SCHEMA_HASH_CHANGED', '$.newSchemaHash', ?)
                WHERE project_id=? AND mcp_id=? AND tool_name=?
                  AND status IN ('ACTIVE', 'PENDING_REVIEW') AND schema_hash<>?
                """, newSchemaHash, projectId, mcpId, toolName, newSchemaHash);
    }

    @Override
    public int markDuplicatePendingSuggestionsStale(String projectId,
                                                    String mcpId,
                                                    String toolName,
                                                    String schemaHash,
                                                    String keepPolicyId) {
        requireAvailable();
        return jdbcTemplate.update("""
                UPDATE ai_ops_mcp_tool_policy
                SET status='STALE',
                    metadata_json=JSON_SET(
                      CASE WHEN JSON_VALID(metadata_json) THEN COALESCE(metadata_json, '{}') ELSE '{}' END,
                      '$.staleReason', 'DUPLICATE_POLICY_SUGGESTION', '$.canonicalPolicyId', ?)
                WHERE project_id=? AND mcp_id=? AND tool_name=? AND schema_hash=?
                  AND status='PENDING_REVIEW' AND review_status<>'HUMAN_REVIEWED' AND policy_id<>?
                """, keepPolicyId, projectId, mcpId, toolName, schemaHash, keepPolicyId);
    }

    private RowMapper<McpToolPolicy> mapper() {
        return (rs, rowNum) -> new McpToolPolicy(
                rs.getLong("id"), rs.getString("policy_id"), rs.getString("project_id"),
                rs.getString("mcp_id"), rs.getString("tool_id"), rs.getString("tool_name"),
                rs.getString("schema_hash"), rs.getString("effect_type"), rs.getString("effect_scope"),
                rs.getString("mutability"), rs.getString("capability"), rs.getString("allowed_actions_json"),
                McpRiskLevel.failClosed(rs.getString("risk_level")),
                rs.getInt("read_only") == 1, rs.getInt("investigate_allowed") == 1,
                rs.getInt("prepare_allowed") == 1, rs.getInt("land_allowed") == 1,
                rs.getInt("requires_approved_package") == 1, rs.getInt("requires_human_approval") == 1,
                rs.getInt("requires_dry_run") == 1,
                rs.getInt("requires_rollback_plan") == 1, rs.getString("argument_policy_json"),
                McpToolPolicyStatus.require(rs.getString("status")),
                McpToolPolicyReviewStatus.require(rs.getString("review_status")),
                rs.getString("reviewed_by"),
                localDateTime(rs.getTimestamp("reviewed_at")), rs.getString("suggested_by"),
                localDateTime(rs.getTimestamp("suggested_at")), rs.getString("metadata_json"),
                localDateTime(rs.getTimestamp("create_time")), localDateTime(rs.getTimestamp("update_time")));
    }

    private RowMapper<McpToolPolicyAdminRecord> adminMapper() {
        return (rs, rowNum) -> new McpToolPolicyAdminRecord(
                rs.getLong("id"), rs.getString("policy_id"), rs.getString("project_id"),
                rs.getString("mcp_id"), rs.getString("tool_id"), rs.getString("tool_name"),
                rs.getString("schema_hash"), rs.getString("effect_type"), rs.getString("effect_scope"),
                rs.getString("mutability"), rs.getString("capability"), rs.getString("allowed_actions_json"),
                rs.getString("risk_level"), rs.getInt("read_only") == 1,
                rs.getInt("investigate_allowed") == 1, rs.getInt("prepare_allowed") == 1,
                rs.getInt("land_allowed") == 1, rs.getInt("requires_approved_package") == 1,
                rs.getInt("requires_human_approval") == 1, rs.getInt("requires_dry_run") == 1,
                rs.getInt("requires_rollback_plan") == 1,
                rs.getString("argument_policy_json"), rs.getString("status"), rs.getString("review_status"),
                rs.getString("reviewed_by"), localDateTime(rs.getTimestamp("reviewed_at")),
                rs.getString("suggested_by"), localDateTime(rs.getTimestamp("suggested_at")),
                rs.getString("metadata_json"), localDateTime(rs.getTimestamp("create_time")),
                localDateTime(rs.getTimestamp("update_time")));
    }

    private void requireAvailable() {
        if (jdbcTemplate == null) throw new IllegalStateException("MCP_TOOL_POLICY_STORE_UNAVAILABLE");
    }

    private int bounded(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private int flag(boolean value) {
        return value ? 1 : 0;
    }

    private Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private java.time.LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
