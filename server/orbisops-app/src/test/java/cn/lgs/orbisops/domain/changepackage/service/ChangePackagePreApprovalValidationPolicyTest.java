package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationOperationAssessment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreApprovalValidationPolicyTest {

    private static final Set<String> ALL_FIELDS = Set.of(
            "operationId",
            "toolName",
            "adapterType",
            "arguments",
            "resourceScope",
            "targetEnvironment",
            "riskLevel",
            "effectType",
            "effectScope",
            "mutability",
            "readOnly",
            "writesTargetResource",
            "requiresChangePackage",
            "requiresApproval");

    private final ChangePackagePreApprovalValidationPolicy policy =
            new ChangePackagePreApprovalValidationPolicy();

    @Test
    void completeLowRiskReadOperationDoesNotRequireTrustedProof() {
        ChangePackageValidationOperation operation = operation(
                "LOW", "VALIDATE_ONLY", "TEST", false, true, ALL_FIELDS);

        ChangePackageValidationOperationAssessment assessment =
                policy.assess("LOW", List.of(operation));

        assertTrue(assessment.valid());
        assertEquals("LOW", assessment.effectiveRiskLevel());
        assertFalse(assessment.trustedProofRequired());
        assertTrue(policy.validationExecutable(operation));
    }

    @Test
    void missingEffectProducesBothShapeAndStalePolicyErrors() {
        Set<String> fields = new java.util.LinkedHashSet<>(ALL_FIELDS);
        fields.remove("effectType");
        ChangePackageValidationOperation operation = operation(
                "MEDIUM", "", "TEST", false, true, fields);

        ChangePackageValidationOperationAssessment assessment =
                policy.assess("MEDIUM", List.of(operation));

        assertTrue(assessment.errors().contains("MISSING_OPERATION_FIELD:effectType:op-1"));
        assertTrue(assessment.errors().contains("POLICY_STALE_OR_UNKNOWN:op-1"));
    }

    @Test
    void targetWriteWithoutChangePackageFailsAndRequiresProof() {
        ChangePackageValidationOperation operation = operation(
                "MEDIUM", "MUTATE_TARGET_RESOURCE", "PRODUCTION", true, false, ALL_FIELDS);

        ChangePackageValidationOperationAssessment assessment =
                policy.assess("LOW", List.of(operation));

        assertTrue(assessment.errors().contains("TARGET_WRITE_REQUIRES_CHANGE_PACKAGE:op-1"));
        assertEquals("MEDIUM", assessment.effectiveRiskLevel());
        assertTrue(assessment.trustedProofRequired());
        assertFalse(policy.validationExecutable(operation));
    }

    @Test
    void riskUsesMaximumAndTempMutationAliasIsValidationExecutable() {
        ChangePackageValidationOperation operation = operation(
                "CRITICAL", "MUTATE_TEMP_RESOURCE", "TEST", false, true, ALL_FIELDS);

        ChangePackageValidationOperationAssessment assessment =
                policy.assess("LOW", List.of(operation));

        assertEquals("CRITICAL", assessment.effectiveRiskLevel());
        assertTrue(assessment.trustedProofRequired());
        assertEquals("MUTATE_EPHEMERAL", operation.effectType());
        assertTrue(policy.validationExecutable(operation));
    }

    private ChangePackageValidationOperation operation(String risk,
                                                       String effectType,
                                                       String effectScope,
                                                       boolean writesTarget,
                                                       boolean requiresChangePackage,
                                                       Set<String> presentFields) {
        return new ChangePackageValidationOperation(
                "op-1",
                "validate_config",
                "MCP",
                "mcp-1",
                "demo-project/config",
                "test",
                risk,
                effectType,
                effectScope,
                "READ_ONLY",
                presentFields,
                writesTarget,
                requiresChangePackage);
    }
}
