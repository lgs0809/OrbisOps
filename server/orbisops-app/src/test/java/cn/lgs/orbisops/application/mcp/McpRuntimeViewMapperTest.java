package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyAdminRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McpRuntimeViewMapperTest {

    @Test
    void runtimeCatalogSurfacesSystemVerifiedPolicyTruthfully() {
        Map<String, Object> view = new McpRuntimeViewMapper(new McpJsonCodec()).runtimeCatalogView(Map.of(
                "toolName", "search",
                "capability", "READ_ONLY",
                "effectType", "READ_EXTERNAL_STATE",
                "riskLevel", "MEDIUM",
                "readOnly", true,
                "disclosureTier", "EXTENSION",
                "policyStatus", "ACTIVE",
                "reviewStatus", "SYSTEM_VERIFIED"));

        assertEquals("ACTIVE", view.get("policyStatus"));
        assertEquals("SYSTEM_VERIFIED", view.get("reviewStatus"));
        assertEquals("平台内置只读工具，已通过系统验证；扩展工具按需激活后披露完整 schema。", view.get("description"));
    }

    @Test
    void legacyBlankSchemaPolicyIsProjectedAsNonExecutableStaleAdminHistory() {
        McpToolPolicyAdminRecord legacy = new McpToolPolicyAdminRecord(
                1L, "legacy-policy", "project-1", "mcp-1", "tool-1", "restart_service", "",
                "EXECUTE_EXTERNAL_ACTION", "PRODUCTION", "PROD_MUTATING", "SERVICE_RESTART",
                "[\"RESTART_SERVICE\"]", "LOW",
                true, true, true, true,
                false, false, false, false,
                "{}", "ACTIVE", "HUMAN_REVIEWED", "admin", null,
                "", null, "{}", null, null);

        Map<String, Object> view = new McpRuntimeViewMapper(new McpJsonCodec()).policyAdminView(legacy);

        assertEquals(true, view.get("legacyInvalid"));
        assertEquals("MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED", view.get("invalidReason"));
        assertEquals("STALE", view.get("status"));
        assertEquals("HIGH", view.get("riskLevel"));
        assertEquals(false, view.get("readOnly"));
        assertEquals(false, view.get("investigateAllowed"));
        assertEquals(false, view.get("prepareAllowed"));
        assertEquals(false, view.get("landAllowed"));
        assertEquals(true, view.get("requiresApprovedPackage"));
        assertEquals(true, view.get("requiresHumanApproval"));
        assertEquals(true, view.get("requiresDryRun"));
        assertFalse(view.containsKey("requiresSandbox"));
        assertEquals(true, view.get("requiresRollbackPlan"));
    }
}
