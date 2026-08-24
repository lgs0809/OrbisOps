package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.OpsRunControlContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OpsPreDispatchCheckpointTest {
    @Test void backgroundWithoutTrustedClaimCannotManufacturePlatformBoundary() {
        Map<String, Object> payload = Map.of("remoteCallExecuted", false);
        assertSame(payload, OpsPreDispatchCheckpoint.bind("project", "run", payload));
        assertFalse(payload.containsKey("dispatchBoundary"));
    }

    @Test void trustedClaimOverridesUserSuppliedIdentities() {
        var metadata = Map.of(OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt",
                OpsWorkSessionClaimMetadata.FENCING_TOKEN, 7);
        try (var ignored = OpsRunControlContext.open(binding(Map.of("metadata", metadata)))) {
            var proof = OpsPreDispatchCheckpoint.bind("project", "run", Map.of(
                    "attemptId", "forged", "fencingToken", 0, "projectId", "forged", "runId", "forged"));
            assertEquals("attempt", proof.get("attemptId")); assertEquals(7, proof.get("fencingToken"));
            assertEquals("project", proof.get("projectId")); assertEquals("run", proof.get("runId"));
            assertEquals("PRE_DISPATCH_DENIED", proof.get("dispatchBoundary"));
            assertThrows(UnsupportedOperationException.class, () -> proof.put("runId", "changed"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"metadata", "attempt", "fence", "fenceType", "foreignProject", "foreignRun"})
    void missingOrForeignTrustedClaimFailsClosed(String failure) {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put(OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt");
        metadata.put(OpsWorkSessionClaimMetadata.FENCING_TOKEN, 7);
        if ("attempt".equals(failure)) metadata.remove(OpsWorkSessionClaimMetadata.ATTEMPT_ID);
        if ("fence".equals(failure)) metadata.remove(OpsWorkSessionClaimMetadata.FENCING_TOKEN);
        if ("fenceType".equals(failure)) metadata.put(OpsWorkSessionClaimMetadata.FENCING_TOKEN, "7");
        Map<String, Object> request = "metadata".equals(failure) ? Map.of() : Map.of("metadata", metadata);
        try (var ignored = OpsRunControlContext.open(binding(request))) {
            assertThrows(IllegalStateException.class, () -> OpsPreDispatchCheckpoint.bind(
                    "foreignProject".equals(failure) ? "foreign" : "project",
                    "foreignRun".equals(failure) ? "foreign" : "run", Map.of("remoteCallExecuted", false)));
        }
    }

    private OpsRunControlContext.Binding binding(Map<String, Object> request) {
        return new OpsRunControlContext.Binding("run", "project", request, new OpsRunCancellationRegistry());
    }
}
