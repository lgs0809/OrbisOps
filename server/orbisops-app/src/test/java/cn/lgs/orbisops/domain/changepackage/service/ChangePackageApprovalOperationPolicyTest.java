package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalOperationAssessment;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageApprovalOperationPolicyTest {

    private static final Set<String> REQUIRED = Set.of(
            "operationId",
            "toolName",
            "adapterType",
            "arguments",
            "resourceScope",
            "riskLevel",
            "targetEnvironment",
            "effectType",
            "effectScope",
            "mutability",
            "readOnly",
            "writesTargetResource",
            "requiresChangePackage",
            "requiresApproval");

    private final ChangePackageApprovalOperationPolicy policy =
            new ChangePackageApprovalOperationPolicy();

    @Test
    void readOnlyLowRiskOperationDoesNotRequireTrustedProof() {
        ChangePackageApprovalOperation operation = operation(false, true, true, "LOW");

        ChangePackageApprovalOperationAssessment assessment =
                policy.verify("LOW", List.of(operation));

        assertEquals("LOW", assessment.effectiveRiskLevel());
        assertFalse(assessment.trustedProofRequired());
    }

    @Test
    void targetWriteRequiresProofAndUsesMaximumRisk() {
        ChangePackageApprovalOperation operation = operation(true, true, true, "CRITICAL");

        ChangePackageApprovalOperationAssessment assessment =
                policy.verify("MEDIUM", List.of(operation));

        assertEquals("CRITICAL", assessment.effectiveRiskLevel());
        assertTrue(assessment.trustedProofRequired());
    }

    @Test
    void targetWriteMustExplicitlyRequireApproval() {
        ChangePackageApprovalOperation operation = operation(true, true, false, "HIGH");

        assertThrows(IllegalStateException.class,
                () -> policy.verify("HIGH", List.of(operation)));
    }

    @Test
    void canonicalHashMismatchIsRejected() {
        ChangePackageApprovalOperation valid = operation(false, true, true, "LOW");
        ChangePackageApprovalOperation tampered = new ChangePackageApprovalOperation(
                valid.raw(),
                valid.operationId(),
                valid.mcpId(),
                valid.toolName(),
                valid.adapterType(),
                valid.resourceScope(),
                valid.riskLevel(),
                valid.targetEnvironment(),
                valid.effectType(),
                valid.effectScope(),
                valid.mutability(),
                valid.presentFields(),
                valid.writesTargetResource(),
                valid.requiresChangePackage(),
                valid.requiresApproval(),
                valid.preconditionsPresent(),
                valid.postCheckPresent(),
                valid.rollbackPlanPresent(),
                valid.rollbackPreconditionPresent(),
                valid.manualFallbackPresent(),
                "tampered",
                valid.preconditionHash(),
                valid.postCheckHash(),
                valid.rollbackHash(),
                valid.operationHash());

        assertThrows(IllegalStateException.class,
                () -> policy.verify("LOW", List.of(tampered)));
    }

    @Test
    void missingRiskFieldRemainsMissingAfterDefaultNormalization() {
        ChangePackageApprovalOperation valid = operation(false, true, true, "HIGH");
        Set<String> fields = new java.util.LinkedHashSet<>(valid.presentFields());
        fields.remove("riskLevel");
        ChangePackageApprovalOperation missingRisk = new ChangePackageApprovalOperation(
                valid.raw(),
                valid.operationId(),
                valid.mcpId(),
                valid.toolName(),
                valid.adapterType(),
                valid.resourceScope(),
                "",
                valid.targetEnvironment(),
                valid.effectType(),
                valid.effectScope(),
                valid.mutability(),
                fields,
                valid.writesTargetResource(),
                valid.requiresChangePackage(),
                valid.requiresApproval(),
                valid.preconditionsPresent(),
                valid.postCheckPresent(),
                valid.rollbackPlanPresent(),
                valid.rollbackPreconditionPresent(),
                valid.manualFallbackPresent(),
                valid.argumentsHash(),
                valid.preconditionHash(),
                valid.postCheckHash(),
                valid.rollbackHash(),
                valid.operationHash());

        assertThrows(IllegalStateException.class,
                () -> policy.verify("HIGH", List.of(missingRisk)));
    }

    private ChangePackageApprovalOperation operation(boolean targetWrite,
                                                     boolean requiresChangePackage,
                                                     boolean requiresApproval,
                                                     String riskLevel) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("operationId", "op-1");
        raw.put("mcpId", "mcp-1");
        raw.put("toolName", targetWrite ? "apply_config" : "query_config");
        raw.put("adapterType", "MCP");
        raw.put("arguments", Map.of("key", "value"));
        raw.put("resourceScope", "demo-project/config");
        raw.put("riskLevel", riskLevel);
        raw.put("targetEnvironment", "prod");
        raw.put("effectType", targetWrite ? "MUTATE_TARGET_RESOURCE" : "READ_EXTERNAL_STATE");
        raw.put("effectScope", targetWrite ? "PRODUCTION" : "TARGET_RESOURCE_READ");
        raw.put("mutability", targetWrite ? "PROD_MUTATING" : "READ_ONLY");
        raw.put("readOnly", !targetWrite);
        raw.put("writesTargetResource", targetWrite);
        raw.put("requiresChangePackage", requiresChangePackage);
        raw.put("requiresApproval", requiresApproval);
        if (targetWrite) {
            raw.put("preconditions", Map.of("expectedValues", Map.of("version", "1")));
            raw.put("postCheck", Map.of("expectedValues", Map.of("version", "2")));
            raw.put("rollbackPlan", Map.of("summary", "restore version 1"));
            raw.put("rollbackPrecondition", Map.of("expectedValues", Map.of("version", "2")));
            raw.put("manualFallback", Map.of("owner", "ops"));
        }
        ChangePackageCanonicalHasher.OperationHashes hashes =
                ChangePackageCanonicalHasher.applyOperationHashes(raw);
        return new ChangePackageApprovalOperation(
                raw,
                "op-1",
                "mcp-1",
                targetWrite ? "apply_config" : "query_config",
                "MCP",
                "demo-project/config",
                riskLevel,
                "prod",
                targetWrite ? "MUTATE_TARGET_RESOURCE" : "READ_EXTERNAL_STATE",
                targetWrite ? "PRODUCTION" : "TARGET_RESOURCE_READ",
                targetWrite ? "PROD_MUTATING" : "READ_ONLY",
                REQUIRED,
                targetWrite,
                requiresChangePackage,
                requiresApproval,
                targetWrite,
                targetWrite,
                targetWrite,
                targetWrite,
                targetWrite,
                hashes.argumentsHash(),
                hashes.preconditionHash(),
                hashes.postCheckHash(),
                hashes.rollbackHash(),
                hashes.operationHash());
    }
}
