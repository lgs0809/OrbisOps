package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyAdminRecord;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcMcpToolPolicyRepositoryTest {

    @Test
    void savePersistsTypedPolicy() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcMcpToolPolicyRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.save(policy());

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void suggestionInsertIsAtomicAndDoesNotUpdateExistingPolicy() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcMcpToolPolicyRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

        assertTrue(repository.saveSuggestionIfAbsent(policy()));
        assertFalse(repository.saveSuggestionIfAbsent(policy()));

        verify(jdbc, org.mockito.Mockito.times(2)).update(
                org.mockito.ArgumentMatchers.contains("INSERT IGNORE INTO ai_ops_mcp_tool_policy"),
                any(Object[].class));
    }

    @Test
    void reviewStatusUpdateUsesAffectedRowAsConflictSignal() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcMcpToolPolicyRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

        assertTrue(repository.updateReviewStatus(
                "project-1", "policy-1", McpToolPolicyStatus.DISABLED,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED, "admin", "{}"));
        assertFalse(repository.updateReviewStatus(
                "project-1", "policy-1", McpToolPolicyStatus.DISABLED,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED, "admin", "{}"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminMapperToleratesLegacyBlankSchemaButStrictMapperStillFailsClosed() throws Exception {
        JdbcMcpToolPolicyRepository repository = repository(mock(JdbcTemplate.class));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("policy_id")).thenReturn("legacy-policy");
        when(rs.getString("project_id")).thenReturn("project-1");
        when(rs.getString("mcp_id")).thenReturn("mcp-1");
        when(rs.getString("tool_id")).thenReturn("tool-1");
        when(rs.getString("tool_name")).thenReturn("restart_service");
        when(rs.getString("schema_hash")).thenReturn("");
        when(rs.getString("risk_level")).thenReturn("LOW");
        when(rs.getString("status")).thenReturn("ACTIVE");
        when(rs.getString("review_status")).thenReturn("HUMAN_REVIEWED");

        RowMapper<McpToolPolicyAdminRecord> adminMapper =
                (RowMapper<McpToolPolicyAdminRecord>) ReflectionTestUtils.invokeMethod(repository, "adminMapper");
        McpToolPolicyAdminRecord admin = adminMapper.mapRow(rs, 0);

        assertTrue(admin.legacyInvalid());
        assertEquals("MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED", admin.invalidReason());

        RowMapper<McpToolPolicy> strictMapper =
                (RowMapper<McpToolPolicy>) ReflectionTestUtils.invokeMethod(repository, "mapper");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> strictMapper.mapRow(rs, 0));
        assertEquals("MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED", error.getMessage());
    }

    @Test
    void missingJdbcStoreFailsClosed() {
        JdbcMcpToolPolicyRepository repository = new JdbcMcpToolPolicyRepository();

        assertFalse(repository.available());
        assertThrows(IllegalStateException.class, () -> repository.save(policy()));
    }

    private JdbcMcpToolPolicyRepository repository(JdbcTemplate jdbcTemplate) {
        JdbcMcpToolPolicyRepository repository = new JdbcMcpToolPolicyRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
        return repository;
    }

    private McpToolPolicy policy() {
        return new McpToolPolicy(0, "policy-1", "project-1", "mcp-1", "tool-1", "search",
                "schema-1", "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY", "READ_ONLY",
                "[\"SEARCH\"]", cn.lgs.orbisops.domain.mcp.model.McpRiskLevel.LOW,
                true, true, true, true, false, false,
                false, false, "{}",
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus.ACTIVE,
                cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "admin", null,
                "", null, "{}", null, null);
    }
}
