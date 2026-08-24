package cn.lgs.orbisops.trigger.application.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import static cn.lgs.orbisops.trigger.application.runtime.WorkflowBoundMcpReadOnlyActivationTest.schema;
import static org.junit.jupiter.api.Assertions.*;

/** Authoritative lifecycle belongs to MCP execution, not the Workflow dispatcher. */
class WorkflowMcpActivationLifecycleTest {
    @Test
    void coreReadonlyUsesCurrentReviewedSchemaWithoutUnnecessaryDisclosureOrActivation() {
        var f = fixture(schema("ACTIVE", true));
        f.requiresActivation = false;
        f.dispatchCompatibility(true);
        assertEquals(0, f.inspections);
        assertTrue(f.disclosures.isEmpty());
        assertFalse(f.activated.get());
        assertEquals(1, f.businessCalls.size());
    }

    @Test
    void alreadyActivatedExtensionDoesNotDiscloseOrEnableAgain() {
        var f = fixture(schema("ACTIVE", true));
        f.activated.set(true);
        f.dispatchCompatibility(true);
        assertEquals(0, f.inspections);
        assertTrue(f.disclosures.isEmpty());
        assertEquals(1, f.businessCalls.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "STALE", "DISABLED"})
    void currentPolicyDenialPrecedesAnyRemoteDisclosure(String policyStatus) {
        var f = fixture(schema(policyStatus, true));
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertEquals(0, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @Test
    void currentMutationFailsReadonlyBeforeInspectOrActivation() {
        var f = fixture(schema("ACTIVE", false));
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertEquals(0, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @Test
    void freshNonProductionMutationCannotBorrowReadonlyActivationIntent() {
        var f = fixture(schema("ACTIVE", true));
        f.disclosedAttributes = Map.of("policyStatus", "ACTIVE", "reviewStatus", "HUMAN_REVIEWED",
                "effectType", "MUTATE_TEST_RESOURCE", "effectScope", "TEST_ENVIRONMENT",
                "mutability", "TEST_MUTATING", "readOnly", false, "riskLevel", "LOW", "prepareAllowed", true);
        SecurityException denied = assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertTrue(denied.getMessage().contains("READ_ONLY_REQUIRED"));
        assertEquals(1, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @Test
    void failedActivationCannotDispatchBusinessOrRecursivelyRedisclose() {
        var f = fixture(schema("ACTIVE", true));
        f.activationFails = true;
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertEquals(1, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
        assertEquals(1, f.disclosures.size());
    }

    @Test
    void missingCoreSchemaIsHydratedOnceWithoutGrantingRunActivation() {
        var f = fixture(Map.of());
        f.requiresActivation = false;
        f.disclosedAttributes = schema("ACTIVE", true);
        f.dispatchCompatibility(true);
        assertEquals(1, f.inspections);
        assertTrue(f.disclosures.isEmpty());
        assertFalse(f.activated.get());
        assertEquals(1, f.businessCalls.size());
    }

    @Test
    void policyRevokedImmediatelyAfterActivationIsCheckedAgainBeforeBusinessDispatch() {
        var f = fixture(schema("ACTIVE", true));
        f.afterActivationAttributes = schema("DISABLED", true);
        SecurityException denied = assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertTrue(denied.getMessage().contains("MCP_POLICY_DISABLED"));
        assertTrue(f.activated.get(), "The revocation happens after the one native activation");
        assertEquals(1, f.inspections);
        assertTrue(f.businessCalls.isEmpty());
    }

    private WorkflowBoundMcpReadOnlyActivationTest.Fixture fixture(Map<String,Object> attributes) {
        return new WorkflowBoundMcpReadOnlyActivationTest.Fixture(attributes, List.of("observe"));
    }
}
