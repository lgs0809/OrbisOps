package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.support.EvidenceTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsTrustedProofServiceTest {

    @Test
    void onlyTrustedSourcesAndPassedStatusesCanBeVerified() {
        OpsTrustedProofService service = EvidenceTestSupport.trustedProofService();
        Map<String, Object> recorded = service.recordTrustedProof(Map.ofEntries(
                Map.entry("proofId", "proof-1"),
                Map.entry("projectId", "project-1"),
                Map.entry("packageId", "cp-1"),
                Map.entry("packageVersion", 2),
                Map.entry("packageHash", "hash-2"),
                Map.entry("proofType", "CI_DRY_RUN"),
                Map.entry("source", "CI_PROVIDER"),
                Map.entry("externalRunId", "ci-1"),
                Map.entry("resultStatus", "PASSED"),
                Map.entry("riskLevel", "HIGH"),
                Map.entry("metadata", Map.of("provider", "github"))), "alice");

        assertEquals("proof-1", recorded.get("proofId"));
        assertTrue(service.verifyTrustedProof(
                "project-1", "cp-1", 2, "hash-2", "HIGH", "CI_DRY_RUN", "ci-1"));
        assertFalse(service.verifyTrustedProof(
                "project-1", "cp-1", 2, "other", "HIGH", "CI_DRY_RUN", "ci-1"));
        assertEquals("CI_PROVIDER", service.findTrustedProof(
                "project-1", "cp-1", 2, "hash-2", "HIGH", "CI_DRY_RUN", "proof-1").get("source"));
    }

    @Test
    void rejectsUntrustedSourceAndInvalidVersion() {
        OpsTrustedProofService service = EvidenceTestSupport.trustedProofService();

        assertThrows(IllegalArgumentException.class, () -> service.recordTrustedProof(Map.of(
                "projectId", "project-1", "packageId", "cp-1", "packageVersion", 2,
                "packageHash", "hash-2", "proofType", "CI_DRY_RUN", "source", "REQUEST",
                "resultStatus", "PASSED", "riskLevel", "HIGH"), "alice"));
        assertThrows(IllegalArgumentException.class, () -> service.recordTrustedProof(Map.of(
                "projectId", "project-1", "packageId", "cp-1", "packageVersion", 0,
                "packageHash", "hash-2", "proofType", "CI_DRY_RUN", "source", "CI_PROVIDER",
                "resultStatus", "PASSED", "riskLevel", "HIGH"), "alice"));
    }

    @Test
    void readinessReflectsExplicitTestMemoryRepository() {
        assertEquals("DEGRADED_MEMORY",
                EvidenceTestSupport.trustedProofService().readiness().get("status"));
    }
}
