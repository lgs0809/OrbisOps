package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAuthoritativeDatasourceEvidenceStoreTest {

    @Test
    void persistsToolResultThenVerifiedEvidenceWithSameDurableReferences() {
        OpsToolResultStore toolResults = mock(OpsToolResultStore.class);
        OpsEvidenceStore evidence = mock(OpsEvidenceStore.class);
        Map<String, Object> summary = Map.of(
                "metricSummary", Map.of("up", 0),
                "endpointMetrics", List.of(Map.of("uri", "/api/demo-project/join", "status", "DOWN")));
        String outputHash = CanonicalObjectHasher.sha256(summary);
        when(toolResults.record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyLong()))
                .thenReturn(Map.of(
                        "resultId", "tool-result-source-1",
                        "outputHash", outputHash,
                        "fullOutputRef", "tool-result://tool-result-source-1",
                        "preview", "preview"));
        when(evidence.record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyBoolean(), any(), anyString()))
                .thenReturn(Map.of("evidenceId", "evidence-source-1", "verified", true));
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .projectId("demo-project")
                .runId("run-source-1")
                .requestedBy("alice")
                .build();

        OpsAuthoritativeDatasourceEvidenceStore.PersistedEvidence persisted =
                new OpsAuthoritativeDatasourceEvidenceStore(toolResults, evidence).persist(
                        request,
                        OpsAuthoritativeDatasourceEvidenceProjector.PROMETHEUS,
                        "http://127.0.0.1:9090",
                        summary);

        assertEquals("tool-result-source-1", persisted.resultId());
        assertEquals("evidence-source-1", persisted.evidenceId());
        assertEquals(outputHash, persisted.outputHash());
        assertEquals("tool-result://tool-result-source-1", persisted.fullOutputRef());
        ArgumentCaptor<String> toolResultId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> evidenceHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fullOutputRef = ArgumentCaptor.forClass(String.class);
        verify(evidence).record(
                org.mockito.ArgumentMatchers.eq("demo-project"),
                org.mockito.ArgumentMatchers.eq("run-source-1"),
                org.mockito.ArgumentMatchers.eq("PROMETHEUS"),
                org.mockito.ArgumentMatchers.eq("http://127.0.0.1:9090"),
                toolResultId.capture(),
                evidenceHash.capture(),
                fullOutputRef.capture(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(true),
                any(),
                org.mockito.ArgumentMatchers.eq("alice"));
        assertEquals("tool-result-source-1", toolResultId.getValue());
        assertEquals(outputHash, evidenceHash.getValue());
        assertEquals("tool-result://tool-result-source-1", fullOutputRef.getValue());
    }

    @Test
    void failsClosedWhenEvidenceStoreDoesNotReturnVerifiedProof() {
        OpsToolResultStore toolResults = mock(OpsToolResultStore.class);
        OpsEvidenceStore evidence = mock(OpsEvidenceStore.class);
        Map<String, Object> summary = Map.of("hits", 1);
        String outputHash = CanonicalObjectHasher.sha256(summary);
        when(toolResults.record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyLong()))
                .thenReturn(Map.of(
                        "resultId", "tool-result-source-2",
                        "outputHash", outputHash,
                        "fullOutputRef", "tool-result://tool-result-source-2",
                        "preview", "preview"));
        when(evidence.record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyBoolean(), any(), anyString()))
                .thenReturn(Map.of("evidenceId", "evidence-source-2", "verified", false));
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .projectId("demo-project")
                .runId("run-source-2")
                .requestedBy("alice")
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new OpsAuthoritativeDatasourceEvidenceStore(toolResults, evidence).persist(
                        request,
                        OpsAuthoritativeDatasourceEvidenceProjector.ELASTICSEARCH,
                        "http://127.0.0.1:9200",
                        summary));

        assertEquals("DATASOURCE_EVIDENCE_NOT_VERIFIED", error.getMessage());
    }
}
