package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperationSafetyDecision;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageLandingOperationSafetyPolicyTest {

    private final ChangePackageLandingOperationSafetyPolicy policy =
            new ChangePackageLandingOperationSafetyPolicy();

    @Test
    void approvedHashesAcceptUntamperedOperation() {
        Map<String, Object> operation = baseOperation();
        ChangePackageCanonicalHasher.applyOperationHashes(operation);

        ChangePackageLandingOperationSafetyDecision decision =
                policy.verifyApprovedHashes(operation);

        assertTrue(decision.allowedOperation());
    }

    @Test
    void missingOrTamperedHashesFailClosed() {
        Map<String, Object> missing = baseOperation();
        ChangePackageLandingOperationSafetyDecision missingDecision =
                policy.verifyApprovedHashes(missing);
        assertFalse(missingDecision.allowedOperation());
        assertEquals("OPERATION_HASH_MISMATCH", missingDecision.reasonCode());

        Map<String, Object> tampered = baseOperation();
        ChangePackageCanonicalHasher.applyOperationHashes(tampered);
        tampered.put("arguments", Map.of("limit", 999));
        ChangePackageLandingOperationSafetyDecision tamperedDecision =
                policy.verifyApprovedHashes(tampered);
        assertFalse(tamperedDecision.allowedOperation());
        assertEquals("OPERATION_HASH_MISMATCH", tamperedDecision.replanTrigger());
    }

    @Test
    void readOnlyOperationDoesNotRequireWriteSafetySpec() {
        ChangePackageLandingOperationSafetyDecision decision =
                policy.verifyTargetWriteSafety(baseOperation());

        assertTrue(decision.allowedOperation());
        assertFalse(policy.targetWrite(baseOperation()));
    }

    @Test
    void productionWriteRequiresPostCheckRollbackPreconditionAndManualFallback() {
        Map<String, Object> operation = writeOperation();

        ChangePackageLandingOperationSafetyDecision missingPostCheck =
                policy.verifyTargetWriteSafety(operation);
        assertEquals("POST_CHECK_REQUIRED", missingPostCheck.reasonCode());

        operation.put("postCheck", Map.of("expectedValues", Map.of("enabled", true)));
        ChangePackageLandingOperationSafetyDecision missingRollback =
                policy.verifyTargetWriteSafety(operation);
        assertEquals("ROLLBACK_PLAN_REQUIRED", missingRollback.reasonCode());

        operation.put("rollbackPlan", Map.of("summary", "restore previous value"));
        ChangePackageLandingOperationSafetyDecision missingPrecondition =
                policy.verifyTargetWriteSafety(operation);
        assertEquals("ROLLBACK_PRECONDITION_REQUIRED", missingPrecondition.reasonCode());

        operation.put("rollbackPrecondition", Map.of("expectedValues", Map.of("enabled", true)));
        ChangePackageLandingOperationSafetyDecision missingFallback =
                policy.verifyTargetWriteSafety(operation);
        assertEquals("MANUAL_FALLBACK_REQUIRED", missingFallback.reasonCode());

        operation.put("manualFallback", Map.of("owner", "ops", "action", "manual restore"));
        assertTrue(policy.verifyTargetWriteSafety(operation).allowedOperation());
        assertTrue(policy.targetWrite(operation));
    }

    @Test
    void explicitTargetWriteFlagIsAuthoritative() {
        Map<String, Object> operation = baseOperation();
        operation.put("writesTargetResource", true);

        assertTrue(policy.targetWrite(operation));
    }

    @Test
    void supplementalChecksMustBeBoundedAndCannotHideAnEmptyOrNestedContract() {
        var operation = writeOperation();
        operation.put("rollbackPlan", Map.of("summary", "stop"));
        operation.put("rollbackPrecondition", Map.of("version", 2));
        operation.put("manualFallback", Map.of("owner", "ops"));
        var check = Map.of("expectedValues", Map.of("version", 2));
        operation.put("postCheck", Map.of("expectedValues", Map.of("healthy", true),
                "additionalChecks", java.util.List.of(check)));
        assertTrue(policy.verifyTargetWriteSafety(operation).allowedOperation());
        operation.put("additionalChecks", java.util.List.of(check));
        assertEquals("POST_CHECK_REQUIRED", policy.verifyTargetWriteSafety(operation).reasonCode());
        operation.remove("additionalChecks");
        for (Object invalid : java.util.List.of("not a list", java.util.List.of(Map.of()),
                java.util.List.of(Map.of("additionalChecks", java.util.List.of(check))),
                java.util.Collections.nCopies(16, check))) {
            operation.put("postCheck", Map.of("expectedValues", Map.of("healthy", true), "additionalChecks", invalid));
            assertEquals("POST_CHECK_REQUIRED", policy.verifyTargetWriteSafety(operation).reasonCode());
        }
    }

    private Map<String, Object> baseOperation() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-1");
        operation.put("adapterType", "MCP");
        operation.put("mcpId", "mcp-1");
        operation.put("toolName", "query_health");
        operation.put("effectType", "READ_EXTERNAL_STATE");
        operation.put("effectScope", "TARGET_RESOURCE_READ");
        operation.put("mutability", "READ_ONLY");
        operation.put("riskLevel", "LOW");
        operation.put("resourceScope", "service-a");
        operation.put("arguments", Map.of("limit", 10));
        operation.put("preconditions", Map.of());
        operation.put("postCheck", Map.of());
        operation.put("rollbackPlan", Map.of());
        return operation;
    }

    private Map<String, Object> writeOperation() {
        Map<String, Object> operation = baseOperation();
        operation.put("effectType", "MUTATE_TARGET_RESOURCE");
        operation.put("effectScope", "PRODUCTION");
        operation.put("mutability", "PROD_MUTATING");
        operation.put("riskLevel", "HIGH");
        operation.remove("postCheck");
        operation.remove("rollbackPlan");
        return operation;
    }
}
