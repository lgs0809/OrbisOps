package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageOperationRisk;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageRiskInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageRiskPolicyTest {

    private final ChangePackageRiskPolicy policy = new ChangePackageRiskPolicy();

    @Test
    void requestCannotDowngradeProductionMutation() {
        ChangePackageRiskPolicy.RiskAssessment assessment = policy.assess(new ChangePackageRiskInput(
                "LOW", List.of(operation("op-1", "LOW", "MUTATE_TARGET_RESOURCE", "PRODUCTION", "PROD_MUTATING")),
                List.of()));

        assertEquals("HIGH", assessment.effectiveRiskLevel());
        assertTrue(assessment.reasons().contains("TARGET_WRITE:op-1"));
    }

    @Test
    void deleteTargetResourceIsAlwaysCritical() {
        ChangePackageRiskPolicy.RiskAssessment assessment = policy.assess(new ChangePackageRiskInput(
                "MEDIUM", List.of(operation("op-delete", "MEDIUM", "DELETE_TARGET_RESOURCE", "PRODUCTION", "DESTRUCTIVE")),
                List.of()));

        assertEquals("CRITICAL", assessment.effectiveRiskLevel());
    }

    @Test
    void sensitiveRepairFilesRaiseRiskWithoutOperation() {
        ChangePackageRiskPolicy.RiskAssessment assessment = policy.assess(new ChangePackageRiskInput(
                "LOW", List.of(), List.of("src/main/resources/db/migration/V12__alter_order.sql")));

        assertEquals("HIGH", assessment.effectiveRiskLevel());
    }

    @Test
    void ephemeralMutationDoesNotBecomeProductionWrite() {
        ChangePackageRiskPolicy.RiskAssessment assessment = policy.assess(new ChangePackageRiskInput(
                "LOW", List.of(operation("op-temp", "LOW", "MUTATE_TEMP_RESOURCE", "SANDBOX", "TEST_MUTATING")),
                List.of()));

        assertEquals("LOW", assessment.effectiveRiskLevel());
        assertTrue(assessment.reasons().isEmpty());
    }

    @Test
    void unknownEffectFailsClosedEvenWhenOperationClaimsLowRisk() {
        ChangePackageRiskPolicy.RiskAssessment assessment = policy.assess(new ChangePackageRiskInput(
                "LOW", List.of(operation("op-unknown", "LOW", "UNKNOWN", "UNKNOWN", "READ_ONLY")),
                List.of()));

        assertEquals("HIGH", assessment.effectiveRiskLevel());
        assertTrue(assessment.reasons().contains("UNKNOWN_EFFECT:op-unknown"));
    }

    private ChangePackageOperationRisk operation(String id,
                                                 String risk,
                                                 String effectType,
                                                 String effectScope,
                                                 String mutability) {
        return new ChangePackageOperationRisk(id, risk, effectType, effectScope, mutability);
    }
}
