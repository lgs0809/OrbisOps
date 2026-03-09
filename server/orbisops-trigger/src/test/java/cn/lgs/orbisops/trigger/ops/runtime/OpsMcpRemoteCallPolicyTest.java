package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMcpRemoteCallPolicyTest {

    private final OpsMcpRemoteCallPolicy policy =
            new OpsMcpRemoteCallPolicy(new OpsMcpToolArgumentPolicyChecker());

    @Test
    void assessmentMustNormalizePolicyMetadataAndExposeStableAuditView() {
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("ops-mcp")
                .mcpId("mcp-1")
                .toolId("tool-1")
                .toolCallStage("PREPARE")
                .toolCapabilities(Map.of("dryRunChange", "execute"))
                .build();
        Map<String, Object> schema = activePolicy(
                false,
                "MEDIUM",
                "MUTATE_TEMP_RESOURCE",
                "VALIDATION_ENVIRONMENT",
                "TEMP_MUTATING",
                List.of("DRY_RUN"));
        schema.put("prepareAllowed", true);
        schema.put("requiresApprovedPackage", false);
        schema.put("requiresDryRun", true);
        schema.put("argumentPolicy", "{\"requiredKeys\":[\"changeId\"]}");

        OpsMcpRemoteCallAssessment assessment = policy.assess(
                config, "dryRunChange", schema);

        assertEquals("MUTATE_EPHEMERAL", assessment.effectType());
        assertEquals("EXECUTE", assessment.capability());
        assertEquals(OpsToolCallStage.PREPARE, assessment.stage());
        assertTrue(assessment.mutating());
        assertTrue(assessment.requiresDryRun());
        assertEquals("tool-1", assessment.toolId());
        assertEquals("PREPARE", assessment.auditMetadata().get("toolCallStage"));
        assertEquals("policy-1", assessment.auditMetadata().get("policyId"));
        assertDoesNotThrow(() -> policy.assertAllowed(
                assessment, "{\"changeId\":\"change-1\"}"));
    }

    @Test
    void systemVerifiedPlatformReadOnlyPolicyIsApprovedForInvestigate() {
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .toolId("tool-1")
                .toolCallStage("INVESTIGATE")
                .build();
        Map<String, Object> schema = activePolicy(
                true,
                "LOW",
                "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ",
                "READ_ONLY",
                List.of("READ"));
        schema.put("reviewStatus", "SYSTEM_VERIFIED");
        schema.put("requiresApprovedPackage", false);
        schema.put("investigateAllowed", true);

        OpsMcpRemoteCallAssessment assessment = policy.assess(config, "queryOrders", schema);

        assertTrue(assessment.platformPolicyApproved());
        assertFalse(assessment.callbackPolicyRequired());
        assertDoesNotThrow(() -> policy.assertAllowed(assessment, "{}"));
    }

    @Test
    void investigateMustApplyArgumentPolicyBeforeRemoteCall() {
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .toolId("tool-1")
                .toolCallStage("INVESTIGATE")
                .build();
        Map<String, Object> schema = activePolicy(
                true,
                "LOW",
                "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ",
                "READ_ONLY",
                List.of("READ"));
        schema.put("requiresApprovedPackage", false);
        schema.put("investigateAllowed", true);
        schema.put("argumentPolicy", Map.of("sqlReadOnlyOnly", true));
        OpsMcpRemoteCallAssessment assessment = policy.assess(
                config, "queryOrders", schema);

        assertDoesNotThrow(() -> policy.assertAllowed(
                assessment, "{\"sql\":\"select * from orders limit 10\"}"));
        SecurityException error = assertThrows(SecurityException.class,
                () -> policy.assertAllowed(
                        assessment,
                        "{\"sql\":\"delete from orders where id=1\"}"));
        assertTrue(error.getMessage().startsWith("MCP_TOOL_ARGUMENT_POLICY_VIOLATION"));
    }

    @Test
    void missingOrInvalidStageMustFailClosedInsteadOfBecomingInvestigate() {
        Map<String, Object> schema = activePolicy(
                true,
                "LOW",
                "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ",
                "READ_ONLY",
                List.of("READ"));
        schema.put("requiresApprovedPackage", false);
        schema.put("investigateAllowed", true);

        OpsMcpRemoteCallAssessment missing = policy.assess(
                OpsMcpServerConfig.builder().toolId("tool-1").build(),
                "queryOrders",
                schema);
        OpsMcpRemoteCallAssessment invalid = policy.assess(
                OpsMcpServerConfig.builder().toolId("tool-1").toolCallStage("LANDIGN").build(),
                "queryOrders",
                schema);

        assertEquals(OpsToolCallStage.UNKNOWN, missing.stage());
        assertEquals(OpsToolCallStage.UNKNOWN, invalid.stage());
        assertThrows(SecurityException.class, () -> policy.assertAllowed(missing, "{\"id\":1}"));
        assertThrows(SecurityException.class, () -> policy.assertAllowed(invalid, "{\"id\":1}"));
    }

    @Test
    void policyStatusReviewAndEffectClassificationMustFailClosed() {
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .toolCallStage("INVESTIGATE")
                .build();

        SecurityException missing = assertThrows(SecurityException.class,
                () -> policy.assertAllowed(policy.assess(config, "queryOrders", Map.of()), "{}"));
        assertTrue(missing.getMessage().startsWith("MCP_POLICY_MISSING"));

        Map<String, Object> pendingSchema = activePolicy(
                true,
                "LOW",
                "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ",
                "READ_ONLY",
                List.of("READ"));
        pendingSchema.put("reviewStatus", "SYSTEM_SUGGESTED");
        SecurityException pending = assertThrows(SecurityException.class,
                () -> policy.assertAllowed(
                        policy.assess(config, "queryOrders", pendingSchema), "{}"));
        assertTrue(pending.getMessage().startsWith("MCP_POLICY_PENDING_REVIEW"));

        Map<String, Object> unknownSchema = activePolicy(
                true,
                "LOW",
                "UNKNOWN",
                "UNKNOWN",
                "UNKNOWN",
                List.of("READ"));
        SecurityException unknown = assertThrows(SecurityException.class,
                () -> policy.assertAllowed(
                        policy.assess(config, "queryOrders", unknownSchema), "{}"));
        assertTrue(unknown.getMessage().startsWith("MCP_TOOL_EFFECT_UNKNOWN"));
    }

    @Test
    void landingRequiresApprovedPackageThenAllowsProjectProdMutations() {
        OpsMcpServerConfig missingPackage = OpsMcpServerConfig.builder()
                .landingApproved(true)
                .toolId("tool-1")
                .build();
        Map<String, Object> schema = activePolicy(
                false,
                "HIGH",
                "MUTATE_TARGET_RESOURCE",
                "TARGET_RESOURCE_WRITE",
                "PROD_MUTATING",
                List.of("UPDATE"));
        schema.put("landAllowed", true);
        schema.put("requiresApprovedPackage", true);

        SecurityException packageRequired = assertThrows(SecurityException.class,
                () -> policy.assertAllowed(
                        policy.assess(missingPackage, "updateConfig", schema), "{\"value\":1}"));
        assertTrue(packageRequired.getMessage().startsWith("MCP_TOOL_REQUIRES_CHANGE_PACKAGE"));

        OpsMcpServerConfig approved = approvedLandingConfig();
        assertDoesNotThrow(() -> policy.assertAllowed(
                policy.assess(approved, "updateConfig", schema), "{\"value\":1}"));

        Map<String, Object> delete = activePolicy(
                false,
                "HIGH",
                "DELETE_TARGET_RESOURCE",
                "TARGET_RESOURCE_WRITE",
                "PROD_MUTATING",
                List.of("DELETE"));
        delete.put("landAllowed", true);
        delete.put("requiresApprovedPackage", true);
        assertDoesNotThrow(() -> policy.assertAllowed(
                policy.assess(approved, "deleteResource", delete), "{\"id\":1}"));
    }

    @Test
    void callbackCompatibilityPolicyMustOnlyRunWithoutPlatformOrLandingApproval() {
        OpsMcpServerConfig investigate = OpsMcpServerConfig.builder().build();
        assertTrue(policy.assess(investigate, "queryOrders", Map.of()).callbackPolicyRequired());

        Map<String, Object> approvedSchema = activePolicy(
                true,
                "LOW",
                "READ_EXTERNAL_STATE",
                "TARGET_RESOURCE_READ",
                "READ_ONLY",
                List.of("READ"));
        assertFalse(policy.assess(
                investigate, "queryOrders", approvedSchema).callbackPolicyRequired());

        assertFalse(policy.assess(
                approvedLandingConfig(), "updateConfig", Map.of()).callbackPolicyRequired());
        assertTrue(policy.requiresChangePackage("MCP_TOOL_REQUIRES_CHANGE_PACKAGE：required"));
        assertFalse(policy.requiresChangePackage("MCP_POLICY_MISSING"));
    }

    private OpsMcpServerConfig approvedLandingConfig() {
        return OpsMcpServerConfig.builder()
                .landingApproved(true)
                .changePackageId("cp-1")
                .approvedPackageHash("hash-1")
                .approvedPackageVersion(1)
                .toolId("tool-1")
                .build();
    }

    private Map<String, Object> activePolicy(boolean readOnly,
                                             String riskLevel,
                                             String effectType,
                                             String effectScope,
                                             String mutability,
                                             List<String> allowedActions) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("policyId", "policy-1");
        schema.put("policyStatus", "ACTIVE");
        schema.put("reviewStatus", "HUMAN_REVIEWED");
        schema.put("readOnly", readOnly);
        schema.put("riskLevel", riskLevel);
        schema.put("effectType", effectType);
        schema.put("effectScope", effectScope);
        schema.put("mutability", mutability);
        schema.put("allowedActions", allowedActions);
        schema.put("requiresApprovedPackage", !readOnly);
        schema.put("investigateAllowed", readOnly);
        schema.put("prepareAllowed", false);
        schema.put("landAllowed", false);
        schema.put("argumentPolicy", Map.of());
        return schema;
    }
}
