package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsMcpRemoteCallPolicyTest {

    private final OpsMcpRemoteCallPolicy policy =
            new OpsMcpRemoteCallPolicy(new OpsMcpToolArgumentPolicyChecker());

    @Test
    void reviewedNonProductionDryRunMayExecuteDuringPrepareAgainstProductionResource() {
        OpsMcpRemoteCallAssessment assessment = assessment(
                "DRY_RUN",
                "VALIDATION_SANDBOX",
                "EPHEMERAL",
                "MEDIUM",
                false,
                true,
                false,
                "restart_service_dry_run");

        assertDoesNotThrow(() -> policy.assertAllowed(assessment, "{}"));
    }

    @Test
    void productionMutationRemainsForbiddenDuringPrepare() {
        OpsMcpRemoteCallAssessment assessment = assessment(
                "EXECUTE_EXTERNAL_ACTION",
                "PRODUCTION",
                "PROD_MUTATING",
                "HIGH",
                false,
                false,
                true,
                "restart_service");

        assertThrows(SecurityException.class, () -> policy.assertAllowed(assessment, "{}"));
    }

    @Test
    void validationThatStillRequiresApprovedPackageCannotRunDuringPrepare() {
        OpsMcpRemoteCallAssessment assessment = assessment(
                "DRY_RUN",
                "VALIDATION_SANDBOX",
                "EPHEMERAL",
                "MEDIUM",
                false,
                true,
                true,
                "restart_service_dry_run");

        assertThrows(SecurityException.class, () -> policy.assertAllowed(assessment, "{}"));
    }

    private OpsMcpRemoteCallAssessment assessment(String effectType,
                                                  String effectScope,
                                                  String mutability,
                                                  String riskLevel,
                                                  boolean readOnly,
                                                  boolean prepareAllowed,
                                                  boolean requiresApprovedPackage,
                                                  String toolName) {
        return new OpsMcpRemoteCallAssessment(
                "policy-1",
                "ACTIVE",
                "HUMAN_REVIEWED",
                effectType,
                effectScope,
                mutability,
                riskLevel,
                readOnly,
                readOnly ? "READ_ONLY" : "MUTATING",
                !readOnly,
                requiresApprovedPackage,
                requiresApprovedPackage,
                false,
                false,
                false,
                prepareAllowed,
                false,
                "prod",
                OpsToolCallStage.PREPARE,
                toolName,
                "tool-1",
                false,
                Map.of());
    }
}
