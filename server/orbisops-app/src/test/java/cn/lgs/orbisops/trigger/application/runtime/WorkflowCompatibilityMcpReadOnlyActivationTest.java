package cn.lgs.orbisops.trigger.application.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static cn.lgs.orbisops.trigger.application.runtime.WorkflowBoundMcpReadOnlyActivationTest.schema;
import static org.junit.jupiter.api.Assertions.*;

/** The legacy DIRECT callback uses executeMcp, rather than the typed Workflow node adapter. */
class WorkflowCompatibilityMcpReadOnlyActivationTest {
    @Test
    void directCompatibilityReadonlyHintActivatesReviewedCurrentRunBeforeDispatch() {
        var f = new WorkflowBoundMcpReadOnlyActivationTest.Fixture(schema("ACTIVE", true), List.of("observe"));
        f.dispatchCompatibility(true);
        assertTrue(f.activated.get());
        assertEquals(1, f.inspections);
        assertEquals(1, f.businessCalls.size());
        assertEquals("run-1", f.businessCalls.get(0).runId());
        assertEquals("ops-agent", f.request.actor());
        assertEquals(true, f.request.requestContext().get("allowReadOnlyActivation"));
        assertFalse(f.request.requestContext().containsKey("requireReadOnly"), "A runtime hint is not a hard invocation restriction");
        var before = f.beforeActivation;
        var restricted = f.disclosures.get(0);
        assertSame(before.config(), restricted.config(), "All provider bindings, headers, authority and deadline stay identical");
        assertEquals(before.actor(), restricted.actor());
        assertEquals(before.trustedLandingRuntime(), restricted.trustedLandingRuntime());
        assertEquals(before.requestedToolName(), restricted.requestedToolName());
        assertEquals(before.input().get("arguments"), restricted.input().get("arguments"));
        var expected = new java.util.LinkedHashMap<>(before.input());
        expected.put("requireReadOnly", true);
        assertEquals(expected, restricted.input());
        assertEquals(expected, cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(restricted.rawInput()));
        assertEquals(restricted.config(), f.businessCalls.get(0));
    }

    @Test
    void readOnlyHintCannotOverrideCurrentMutatingPolicy() {
        var f = new WorkflowBoundMcpReadOnlyActivationTest.Fixture(schema("ACTIVE", false), List.of("observe"));
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "STALE", "DISABLED"})
    void compatibilityMappingStillRequiresCurrentReviewedPolicy(String policyStatus) {
        var f = new WorkflowBoundMcpReadOnlyActivationTest.Fixture(schema(policyStatus, true), List.of("observe"));
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @Test
    void compatibilityMappingCannotBorrowAuthorizationFromAnotherTool() {
        var f = new WorkflowBoundMcpReadOnlyActivationTest.Fixture(schema("ACTIVE", true), List.of("another-tool"));
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertEquals(0, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @Test
    void freshDisclosureRemainsAuthoritativeOverTheOldReadOnlyHint() {
        var f = new WorkflowBoundMcpReadOnlyActivationTest.Fixture(schema("ACTIVE", true), List.of("observe"));
        f.disclosedAttributes = schema("PENDING_REVIEW", true);
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(true));
        assertEquals(1, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @Test
    void hintFalseDoesNotEnableAnyExtensionOrAcquireLandingAuthority() {
        var f = new WorkflowBoundMcpReadOnlyActivationTest.Fixture(schema("ACTIVE", true), List.of("observe"));
        assertThrows(SecurityException.class, () -> f.dispatchCompatibility(false));
        assertEquals(0, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
        assertFalse(f.mapped.trustedLandingRuntime());
        assertEquals(java.util.Map.of(), f.request.landingContext());
    }
}
