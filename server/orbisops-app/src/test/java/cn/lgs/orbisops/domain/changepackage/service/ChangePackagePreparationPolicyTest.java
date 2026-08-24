package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationAssessment;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationContext;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationDecision;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationProof;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationPolicyTest {

    private final ChangePackagePreparationPolicy policy = new ChangePackagePreparationPolicy();

    @Test
    void humanOnlyTypeCannotBecomeReviewReadyEvenWhenExecutionProofsPass() {
        var original = context(new ChangePackagePreparationProof("PASSED", "TOOL_EXECUTED", false), true);
        for (String type : List.of("MANUAL_REQUIRED", "NEEDS_HUMAN_DESIGN", "NO_ACTION_REQUIRED")) {
            var decision = policy.decide(new ChangePackagePreparationContext(original.preflight(), original.dryRun(),
                    original.operations(), original.toolBindingsComplete(), original.trustedEvidencePresent(),
                    original.repairIntent(), type, original.requestedRiskLevel(), original.changedFiles()));
            assertEquals(ChangePackageStatus.VALIDATION_FAILED, decision.status());
            assertTrue(decision.limitations().stream().anyMatch(v -> v.startsWith("HUMAN_ONLY_PACKAGE_HAS_EXECUTABLE_OPERATIONS")));
        }
    }

    @Test
    void missingOnlyExplicitValidationProofKeepsExecutablePackageForValidation() {
        ChangePackagePreparationDecision decision = policy.decide(context(
                new ChangePackagePreparationProof("NOT_SUPPORTED", "SERVER", false),
                true));

        assertEquals(ChangePackagePreparationAssessment.VALIDATION_REQUIRED, decision.assessment());
        assertEquals(ChangePackageType.MCP_OPERATION_PACKAGE, decision.packageType());
        assertEquals(ChangePackageStatus.VALIDATION_FAILED, decision.status());
        assertEquals("MISSING_VALIDATION_PROOF", decision.reasonCode());
    }

    @Test
    void missingTrustedEvidenceStillFallsBackToManualRequired() {
        ChangePackagePreparationDecision decision = policy.decide(context(
                new ChangePackagePreparationProof("NOT_SUPPORTED", "SERVER", false),
                false));

        assertEquals(ChangePackagePreparationAssessment.MANUAL_REQUIRED, decision.assessment());
        assertEquals(ChangePackageType.MANUAL_REQUIRED, decision.packageType());
        assertEquals(ChangePackageStatus.VALIDATION_FAILED, decision.status());
    }

    @Test
    void failedValidationProofDoesNotRemainExecutable() {
        ChangePackagePreparationDecision decision = policy.decide(context(
                new ChangePackagePreparationProof("FAILED", "TOOL_EXECUTED", false),
                true));

        assertEquals(ChangePackagePreparationAssessment.NEEDS_REFINEMENT, decision.assessment());
        assertEquals(ChangePackageType.NEEDS_HUMAN_DESIGN, decision.packageType());
        assertEquals(ChangePackageStatus.VALIDATION_FAILED, decision.status());
    }

    @Test
    void trustedValidationProofMakesPackageReadyForReview() {
        ChangePackagePreparationDecision decision = policy.decide(context(
                new ChangePackagePreparationProof("PASSED", "TOOL_EXECUTED", false),
                true));

        assertEquals(ChangePackagePreparationAssessment.ACCEPTABLE, decision.assessment());
        assertEquals(ChangePackageType.MCP_OPERATION_PACKAGE, decision.packageType());
        assertEquals(ChangePackageStatus.READY_FOR_REVIEW, decision.status());
        assertEquals("READY_FOR_REVIEW", decision.reasonCode());
    }

    private ChangePackagePreparationContext context(ChangePackagePreparationProof dryRun,
                                                    boolean trustedEvidencePresent) {
        return new ChangePackagePreparationContext(
                new ChangePackagePreparationProof("PASSED", "SERVER_PREFLIGHT", false),
                dryRun,
                List.of(
                        new ChangePackagePreparationOperation(
                                "validate-restart",
                                "restart_service_dry_run",
                                "order-service-control-mcp",
                                "DRY_RUN",
                                "VALIDATION_SANDBOX",
                                "EPHEMERAL",
                                "MEDIUM",
                                "service-control://order-service",
                                false),
                        new ChangePackagePreparationOperation(
                                "restart-service",
                                "restart_service",
                                "order-service-control-mcp",
                                "EXECUTE_EXTERNAL_ACTION",
                                "PRODUCTION",
                                "PROD_MUTATING",
                                "HIGH",
                                "service-control://order-service",
                                true)),
                true,
                trustedEvidencePresent,
                false,
                ChangePackageType.MCP_OPERATION_PACKAGE.name(),
                "HIGH",
                List.of());
    }
}
