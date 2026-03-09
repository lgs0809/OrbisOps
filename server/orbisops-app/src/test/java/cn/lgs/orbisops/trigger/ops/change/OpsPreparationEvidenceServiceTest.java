package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsPreparationEvidenceServiceTest {

    @Test
    void missingCanonicalRunProducesEmptyBundleWithoutStoreAccess() {
        OpsEvidenceStore store = mock(OpsEvidenceStore.class);
        OpsPreparationEvidenceService service = service(store);

        OpsPreparationEvidenceService.EvidenceBundle bundle = service.resolve(
                "project-1",
                Map.of("question", "investigate"));

        assertFalse(bundle.present());
        assertTrue(bundle.trustedEvidence().isEmpty());
        assertTrue(bundle.evidenceRefs().isEmpty());
        verify(store, never()).listForRun(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void canonicalRunRequiresInitializedEvidenceStore() {
        OpsPreparationEvidenceService service = service(null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.resolve(
                        "project-1",
                        Map.of("runId", "run-1")));

        assertTrue(error.getMessage().contains("Evidence Store 未初始化"));
    }

    @Test
    void onlyCompleteAuthoritativeReferencesAreReturnedAndProjected() {
        OpsEvidenceStore store = mock(OpsEvidenceStore.class);
        String outputHash = "a".repeat(64);
        Map<String, Object> complete = new LinkedHashMap<>();
        complete.put("evidenceId", "evidence-1");
        complete.put("toolResultId", "tool-result-1");
        complete.put("outputHash", outputHash);
        complete.put("fullOutputRef", "minio://proof/1");
        complete.put("runId", "run-1");
        complete.put("summary", "authoritative proof");
        complete.put("verified", true);
        Map<String, Object> incomplete = Map.of(
                "evidenceId", "evidence-2",
                "toolResultId", "tool-result-2",
                "outputHash", "",
                "fullOutputRef", "minio://proof/2",
                "runId", "run-1",
                "verified", true);
        Map<String, Object> unverified = Map.of(
                "evidenceId", "evidence-3",
                "toolResultId", "tool-result-3",
                "outputHash", outputHash,
                "fullOutputRef", "minio://proof/3",
                "runId", "run-1",
                "verified", false);
        when(store.listForRun("project-1", "run-1", 200))
                .thenReturn(List.of(complete, incomplete, unverified));
        OpsPreparationEvidenceService service = service(store);

        OpsPreparationEvidenceService.EvidenceBundle bundle = service.resolve(
                "project-1",
                Map.of("sourceRunId", "run-1"));
        complete.put("summary", "mutated after resolve");

        assertTrue(bundle.present());
        assertEquals(1, bundle.trustedEvidence().size());
        assertEquals(
                "authoritative proof",
                bundle.trustedEvidence().get(0).get("summary"));
        assertEquals(List.of(Map.of(
                "evidenceId", "evidence-1",
                "toolResultId", "tool-result-1",
                "outputHash", outputHash,
                "fullOutputRef", "minio://proof/1",
                "runId", "run-1")), bundle.evidenceRefs());
        verify(store).listForRun("project-1", "run-1", 200);
    }

    private OpsPreparationEvidenceService service(OpsEvidenceStore store) {
        return new OpsPreparationEvidenceService(() -> store);
    }
}
