package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.mcp.McpPolicyQueryService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptor;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChangePackageActionPolicyBinderTest {

    @Test
    void reviewedPolicyOverridesModelClaimedGovernanceMetadata() {
        McpPolicyQueryService policies = mock(McpPolicyQueryService.class);
        McpToolPolicy restart = policy(
                "policy-restart", "service-control", "restart_service",
                "sha256:restart", "EXECUTE_EXTERNAL_ACTION", "PRODUCTION", "PROD_MUTATING",
                McpRiskLevel.HIGH, false, false, true, true, true, true);
        when(policies.policyModels("p1", 1000)).thenReturn(List.of(restart));
        OpsChangePackageActionPolicyBinder binder = binder(policies);

        Map<String, Object> bound = binder.bind("p1", List.of(Map.ofEntries(
                Map.entry("operationId", "restart"),
                Map.entry("mcpId", "service-control"),
                Map.entry("toolName", "restart_service"),
                Map.entry("adapterType", "LOCAL_SHELL"),
                Map.entry("targetEnvironment", "dev"),
                Map.entry("effectType", "NO_EFFECT"),
                Map.entry("effectScope", "LOCAL"),
                Map.entry("mutability", "READ_ONLY"),
                Map.entry("riskLevel", "LOW"),
                Map.entry("readOnly", true),
                Map.entry("writesTargetResource", false),
                Map.entry("schemaHash", "forged"),
                Map.entry("policyBound", false),
                Map.entry("arguments", Map.of("service", "orders"))))).get(0);

        assertTrue(Boolean.TRUE.equals(bound.get("policyBound")));
        assertEquals("MCP", bound.get("adapterType"));
        assertEquals("", bound.get("targetEnvironment"));
        assertEquals("sha256:restart", bound.get("schemaHash"));
        assertEquals("EXECUTE_EXTERNAL_ACTION", bound.get("effectType"));
        assertEquals("PRODUCTION", bound.get("effectScope"));
        assertEquals("PROD_MUTATING", bound.get("mutability"));
        assertEquals("HIGH", bound.get("riskLevel"));
        assertEquals(false, bound.get("readOnly"));
        assertEquals(true, bound.get("writesTargetResource"));
        assertEquals(true, bound.get("requiresChangePackage"));
        assertEquals(true, bound.get("requiresApproval"));
        assertEquals(true, bound.get("requiresDryRun"));
        assertEquals(Map.of("service", "orders"), bound.get("arguments"));
    }

    @Test
    void unknownBindingIsForcedFailClosed() {
        McpPolicyQueryService policies = mock(McpPolicyQueryService.class);
        when(policies.policyModels("p1", 1000)).thenReturn(List.of());
        OpsChangePackageActionPolicyBinder binder = binder(policies);

        Map<String, Object> bound = binder.bind("p1", List.of(Map.of(
                "mcpId", "invented",
                "toolName", "restart_everything",
                "adapterType", "LOCAL_SHELL",
                "targetEnvironment", "prod",
                "effectType", "DRY_RUN",
                "mutability", "READ_ONLY",
                "readOnly", true,
                "writesTargetResource", false,
                "schemaHash", "forged",
                "policyBound", true))).get(0);

        assertEquals(false, bound.get("policyBound"));
        assertEquals("MCP", bound.get("adapterType"));
        assertEquals("", bound.get("targetEnvironment"));
        assertEquals("UNKNOWN", bound.get("effectType"));
        assertEquals("UNKNOWN", bound.get("effectScope"));
        assertEquals("UNKNOWN", bound.get("mutability"));
        assertEquals("HIGH", bound.get("riskLevel"));
        assertEquals(false, bound.get("readOnly"));
        assertEquals(true, bound.get("writesTargetResource"));
        assertFalse(bound.containsKey("schemaHash"));
    }

    @Test
    void proposalCatalogContainsOnlyReviewedControlledActions() {
        McpPolicyQueryService policies = mock(McpPolicyQueryService.class);
        McpToolPolicy validation = policy(
                "policy-dry", "service-control", "restart_service_dry_run",
                "sha256:dry", "DRY_RUN", "VALIDATION_SANDBOX", "EPHEMERAL",
                McpRiskLevel.MEDIUM, false, true, false, false, false, false);
        McpToolPolicy restart = policy(
                "policy-restart", "service-control", "restart_service",
                "sha256:restart", "EXECUTE_EXTERNAL_ACTION", "PRODUCTION", "PROD_MUTATING",
                McpRiskLevel.HIGH, false, false, true, true, true, true);
        McpToolPolicy readOnly = policy(
                "policy-read", "metrics", "query",
                "sha256:read", "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY",
                McpRiskLevel.LOW, true, true, false, false, false, false);
        when(policies.policyModels("p1", 1000)).thenReturn(List.of(validation, restart, readOnly));
        OpsChangePackageActionPolicyBinder binder = binder(policies);

        List<Map<String, Object>> catalog = binder.proposalCatalog("p1");

        assertEquals(2, catalog.size());
        assertEquals("PRE_APPROVAL_VALIDATION", catalog.get(0).get("purpose"));
        assertEquals("APPROVED_LANDING_ACTION", catalog.get(1).get("purpose"));
        assertTrue(catalog.stream().noneMatch(item -> "query".equals(item.get("toolName"))));
    }

    @Test
    void serviceControlArgumentsUseAuthoritativeResourceIdentityAndObservedVersionFlow() {
        McpPolicyQueryService policies = mock(McpPolicyQueryService.class);
        McpToolPolicy dryRun = policy(
                "policy-dry", "service-control", "restart_service_dry_run",
                "sha256:dry", "DRY_RUN", "VALIDATION_SANDBOX", "EPHEMERAL",
                McpRiskLevel.MEDIUM, false, true, false, false, false, false);
        McpToolPolicy restart = policy(
                "policy-restart", "service-control", "restart_service",
                "sha256:restart", "EXECUTE_EXTERNAL_ACTION", "PRODUCTION", "PROD_MUTATING",
                McpRiskLevel.HIGH, false, false, true, true, true, true);
        when(policies.policyModels("p1", 1000)).thenReturn(List.of(dryRun, restart));

        ProjectMcpRuntimeDescriptorApplicationService descriptors = mock(ProjectMcpRuntimeDescriptorApplicationService.class);
        ProjectMcpRuntimeDescriptor descriptor = mock(ProjectMcpRuntimeDescriptor.class);
        ProjectMcpDefinition mcp = mock(ProjectMcpDefinition.class);
        ProjectResourceDefinition resource = mock(ProjectResourceDefinition.class);
        when(mcp.resourceType()).thenReturn("service_control");
        when(resource.endpoint()).thenReturn("service-control://order-service");
        when(resource.environment()).thenReturn("prod");
        when(descriptor.mcp()).thenReturn(mcp);
        when(descriptor.resource()).thenReturn(resource);
        when(descriptors.resolveEnabled("p1", "service-control")).thenReturn(Optional.of(descriptor));
        OpsChangePackageActionPolicyBinder binder = new OpsChangePackageActionPolicyBinder(policies, descriptors);

        List<Map<String, Object>> bound = binder.bind("p1", List.of(
                Map.of(
                        "operationId", "validate",
                        "mcpId", "service-control",
                        "toolName", "restart_service_dry_run",
                        "arguments", Map.of("service", "model-guessed-service", "expectedVersion", 0)),
                Map.of(
                        "operationId", "land",
                        "mcpId", "service-control",
                        "toolName", "restart_service",
                        "arguments", Map.of("service", "another-guess"))));

        assertEquals(Map.of("service", "order-service"), bound.get(0).get("arguments"));
        assertEquals(Map.of("service", "order-service"), bound.get(1).get("arguments"));
        assertEquals("MCP", bound.get(0).get("adapterType"));
        assertEquals("MCP", bound.get(1).get("adapterType"));
        assertEquals("prod", bound.get(0).get("targetEnvironment"));
        assertEquals("prod", bound.get(1).get("targetEnvironment"));
        assertEquals(false, bound.get(0).get("writesTargetResource"));
        assertEquals(true, bound.get(1).get("writesTargetResource"));
        assertEquals("service-control://order-service", bound.get(0).get("resourceScope"));
        assertEquals("service-control://order-service", bound.get(1).get("resourceScope"));
        List<Map<String, Object>> catalog = binder.proposalCatalog("p1");
        assertEquals(Map.of("service", "order-service"), catalog.get(0).get("fixedArguments"));
        assertEquals("MCP", catalog.get(0).get("adapterType"));
        assertEquals("prod", catalog.get(0).get("targetEnvironment"));
        assertEquals(false, catalog.get(0).get("writesTargetResource"));
        assertEquals(true, catalog.get(1).get("writesTargetResource"));
        assertEquals("OBSERVE_FROM_DRY_RUN", catalog.get(0).get("expectedVersionSource"));
        assertEquals("FREEZE_FROM_PRE_APPROVAL_DRY_RUN", catalog.get(1).get("expectedVersionSource"));
    }

    private OpsChangePackageActionPolicyBinder binder(McpPolicyQueryService policies) {
        ProjectMcpRuntimeDescriptorApplicationService descriptors = mock(ProjectMcpRuntimeDescriptorApplicationService.class);
        return new OpsChangePackageActionPolicyBinder(policies, descriptors);
    }

    private McpToolPolicy policy(String policyId,
                                 String mcpId,
                                 String toolName,
                                 String schemaHash,
                                 String effectType,
                                 String effectScope,
                                 String mutability,
                                 McpRiskLevel riskLevel,
                                 boolean readOnly,
                                 boolean prepareAllowed,
                                 boolean landAllowed,
                                 boolean requiresApprovedPackage,
                                 boolean requiresHumanApproval,
                                 boolean requiresDryRun) {
        McpToolPolicy policy = mock(McpToolPolicy.class);
        when(policy.activeAndHumanReviewed()).thenReturn(true);
        when(policy.policyId()).thenReturn(policyId);
        when(policy.mcpId()).thenReturn(mcpId);
        when(policy.toolName()).thenReturn(toolName);
        when(policy.schemaHash()).thenReturn(schemaHash);
        when(policy.effectType()).thenReturn(effectType);
        when(policy.effectScope()).thenReturn(effectScope);
        when(policy.mutability()).thenReturn(mutability);
        when(policy.riskLevel()).thenReturn(riskLevel);
        when(policy.readOnly()).thenReturn(readOnly);
        when(policy.prepareAllowed()).thenReturn(prepareAllowed);
        when(policy.landAllowed()).thenReturn(landAllowed);
        when(policy.requiresApprovedPackage()).thenReturn(requiresApprovedPackage);
        when(policy.requiresHumanApproval()).thenReturn(requiresHumanApproval);
        when(policy.requiresDryRun()).thenReturn(requiresDryRun);
        when(policy.requiresRollbackPlan()).thenReturn(requiresHumanApproval);
        when(policy.argumentPolicyJson()).thenReturn("{}");
        when(policy.status()).thenReturn(cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus.ACTIVE);
        when(policy.reviewStatus()).thenReturn(cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus.HUMAN_REVIEWED);
        return policy;
    }
}
