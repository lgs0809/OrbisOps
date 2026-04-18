package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsLandingRecoveryPostCheckVerifierTest {

    private final OpsLandingRecoveryPostCheckVerifier verifier =
            new OpsLandingRecoveryPostCheckVerifier();

    @Test
    void skipsAuthoritativeReadForNonTargetWrite() {
        OpsLandingOperationExecutor executor = mock(OpsLandingOperationExecutor.class);

        Map<String, Object> result = verifier.verify(
                Map.of("effectType", "READ_ONLY"),
                executor,
                Map.of());

        assertTrue(result.isEmpty());
        verify(executor, never()).readCurrentState(
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void requiresApprovedExpectationsForRecoveredProductionWrite() {
        Map<String, Object> result = verifier.verify(
                Map.of("writesTargetResource", true),
                mock(OpsLandingOperationExecutor.class),
                Map.of());

        assertEquals("POST_CHECK_REQUIRED", result.get("reasonCode"));
    }

    @Test
    void reportsMismatchWithoutFailingOnMissingActualValue() {
        OpsLandingOperationExecutor executor = mock(OpsLandingOperationExecutor.class);
        Map<String, Object> operation = Map.of(
                "writesTargetResource", true,
                "postCheck", Map.of("expectedValues", Map.of("mode", "ON")));
        when(executor.readCurrentState(operation, Map.of("runId", "landing-1")))
                .thenReturn(Map.of(
                        "status", "SUCCEEDED",
                        "currentState", Map.of()));

        Map<String, Object> result = verifier.verify(
                operation,
                executor,
                Map.of("runId", "landing-1"));

        assertEquals("POST_CHECK_FAILED", result.get("reasonCode"));
        assertEquals("mode", result.get("field"));
        assertEquals("ON", result.get("expected"));
        assertTrue(result.containsKey("actual"));
    }
}
