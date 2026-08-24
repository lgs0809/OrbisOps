package cn.lgs.orbisops.domain.mcp.service;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaExposureTier;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReview;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicySuggestion;
import cn.lgs.orbisops.domain.mcp.model.McpToolRuntimeAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolPolicyGovernanceTest {

    private final McpToolPolicyGovernance governance = new McpToolPolicyGovernance();

    @Test
    void explicitlyReadOnlyValidationCanBePublishedWithoutAllowingDisguisedWrites() {
        for (String effect : List.of("VALIDATE_ONLY", "DRY_RUN")) {
            governance.validateForHumanPublish(review(effect, "SANDBOX", "READ_ONLY",
                    true, false, false, false, McpRiskLevel.LOW, McpSchemaExposureTier.EXTENSION));
            for (String mutability : List.of("TEST_MUTATING", "EPHEMERAL", "PROD_MUTATING")) {
                assertThrows(IllegalArgumentException.class, () -> governance.validateForHumanPublish(
                        review(effect, "SANDBOX", mutability, true, false, false, false,
                                McpRiskLevel.LOW, McpSchemaExposureTier.EXTENSION)));
            }
            assertThrows(IllegalArgumentException.class, () -> governance.validateForHumanPublish(
                    review(effect, "TARGET_RESOURCE_WRITE", "READ_ONLY", true, false, true, true,
                            McpRiskLevel.HIGH, McpSchemaExposureTier.EXTENSION)));
        }
    }

    @Test
    void unknownClassificationCannotBePublished() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> governance.validateForHumanPublish(review("UNKNOWN", "UNKNOWN", "UNKNOWN",
                        true, true, false, false, McpRiskLevel.LOW, McpSchemaExposureTier.CORE)));

        assertTrue(error.getMessage().contains("MCP_POLICY_CLASSIFICATION_REQUIRED"));
    }

    @Test
    void targetWriteRequiresPackageAndHumanApproval() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> governance.validateForHumanPublish(review("MUTATE_TARGET_RESOURCE", "PRODUCTION",
                        "PROD_MUTATING", false, false, true, false,
                        McpRiskLevel.HIGH, McpSchemaExposureTier.EXTENSION)));

        assertTrue(error.getMessage().contains("MCP_POLICY_TARGET_WRITE_REQUIRES_APPROVAL"));
    }

    @Test
    void coreExposureOnlyAcceptsLowRiskReadOnlyInvestigationTool() {
        governance.validateForHumanPublish(review("READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY",
                true, true, false, false, McpRiskLevel.LOW, McpSchemaExposureTier.CORE));

        assertThrows(IllegalArgumentException.class,
                () -> governance.validateForHumanPublish(review("READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ",
                        "READ_ONLY", true, true, true, false,
                        McpRiskLevel.LOW, McpSchemaExposureTier.CORE)));
    }

    @Test
    void fallbackReadOnlySuggestionRemainsPendingAndAutoCallableBeforeApprovalIsFalseAtPersistenceLayer() {
        McpToolPolicySuggestion suggestion = governance.fallbackSuggestion(
                true, McpRiskLevel.LOW, List.of("SEARCH"), true);

        assertEquals("READ_EXTERNAL_STATE", suggestion.effectType());
        assertEquals(McpToolPolicyReviewStatus.SYSTEM_SUGGESTED, suggestion.reviewStatus());
        assertTrue(suggestion.investigateAllowed());
        assertFalse(suggestion.requiresApprovedPackage());
    }

    @Test
    void unknownMutatingFallbackIsHighRiskAndNotPreApprovalExecutable() {
        McpToolPolicySuggestion suggestion = governance.fallbackSuggestion(
                false, McpRiskLevel.HIGH, List.of("UNKNOWN_MUTATING"), false);

        assertEquals(McpRiskLevel.HIGH, suggestion.riskLevel());
        assertEquals(McpToolPolicyReviewStatus.AI_SUGGESTED, suggestion.reviewStatus());
        assertFalse(suggestion.investigateAllowed());
        assertFalse(suggestion.prepareAllowed());
        assertTrue(suggestion.requiresApprovedPackage());
        assertTrue(suggestion.requiresHumanApproval());
    }

    @Test
    void preApprovalAllowsReviewedEvidenceAndNonProductionValidationOnly() {
        assertTrue(governance.isPreApprovalExecutable(new McpToolRuntimeAccess(
                "READ_EXTERNAL_STATE", "TARGET_RESOURCE_READ", "READ_ONLY", McpRiskLevel.LOW,
                true, true, false, false)));
        assertTrue(governance.isPreApprovalExecutable(new McpToolRuntimeAccess(
                "DRY_RUN", "SANDBOX", "DRY_RUN_ONLY", McpRiskLevel.HIGH,
                false, false, true, true)));
    }

    @Test
    void preApprovalRejectsProductionMutationAndUnknownEffect() {
        assertFalse(governance.isPreApprovalExecutable(new McpToolRuntimeAccess(
                "MUTATE_TARGET_RESOURCE", "PRODUCTION", "PROD_MUTATING", McpRiskLevel.HIGH,
                false, false, true, true)));
        assertFalse(governance.isPreApprovalExecutable(new McpToolRuntimeAccess(
                "UNKNOWN", "UNKNOWN", "UNKNOWN", McpRiskLevel.HIGH,
                false, false, false, true)));
    }

    private McpToolPolicyReview review(String effectType,
                                       String effectScope,
                                       String mutability,
                                       boolean readOnly,
                                       boolean investigateAllowed,
                                       boolean requiresApprovedPackage,
                                       boolean requiresHumanApproval,
                                       McpRiskLevel riskLevel,
                                       McpSchemaExposureTier exposureTier) {
        return new McpToolPolicyReview(effectType, effectScope, mutability, List.of("SEARCH"), readOnly,
                investigateAllowed, requiresApprovedPackage, requiresHumanApproval, riskLevel, exposureTier);
    }
}
