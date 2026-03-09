package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeResourceContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAuthoritativeDatasourceEvidenceProjectorTest {

    @Test
    void projectsOnlyDurableDatasourceEvidenceIntoCurrentRuntimeTrace() {
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsLlmTraceContext.Trace trace = new OpsLlmTraceContext.Trace(
                events,
                null,
                "SUB_AGENT:prometheus-agent",
                "sub-agent-PROMETHEUS",
                "SUB_AGENT",
                "prometheus-agent",
                "PROMETHEUS");
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .projectId("project-1")
                .requestedBy("alice")
                .build();
        Map<String, Object> bounded = Map.of("window", "5m", "samples", List.of("one", "two"));
        String outputHash = CanonicalObjectHasher.sha256(bounded);
        OpsAuthoritativeDatasourceEvidenceStore store = mock(OpsAuthoritativeDatasourceEvidenceStore.class);
        when(store.persist(eq(request), eq(OpsAuthoritativeDatasourceEvidenceProjector.PROMETHEUS),
                eq("http://127.0.0.1:9090"), any())).thenReturn(
                new OpsAuthoritativeDatasourceEvidenceStore.PersistedEvidence(
                        "tool-result-1",
                        "evidence-1",
                        outputHash,
                        "tool-result://tool-result-1",
                        bounded,
                        "2026-08-09 21:30:00"));

        OpsLlmTraceContext.withTrace(trace, () -> {
            new OpsAuthoritativeDatasourceEvidenceProjector(store).record(
                    request,
                    OpsAuthoritativeDatasourceEvidenceProjector.PROMETHEUS,
                    "http://127.0.0.1:9090",
                    Map.of("window", "5m"));
            return null;
        });

        assertEquals(1, events.size());
        OpsRuntimeEvent event = events.get(0);
        assertEquals("SOURCE_QUERY_FINISHED", event.getEventType());
        assertEquals("SUCCEEDED", event.getStatus());
        assertEquals(true, event.getPayload().get("verified"));
        assertEquals("tool-result-1", event.getPayload().get("resultId"));
        assertEquals("evidence-1", event.getPayload().get("evidenceId"));
        assertEquals("tool-result://tool-result-1", event.getPayload().get("fullOutputRef"));
        assertEquals(outputHash, event.getPayload().get("outputHash"));
        assertEquals(bounded, event.getPayload().get("structuredSummary"));
    }

    @Test
    void doesNotPersistDatasourceEvidenceOutsideRuntimeTrace() {
        OpsAuthoritativeDatasourceEvidenceStore store = mock(OpsAuthoritativeDatasourceEvidenceStore.class);
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .projectId("project-1")
                .requestedBy("alice")
                .build();

        new OpsAuthoritativeDatasourceEvidenceProjector(store).record(
                request,
                OpsAuthoritativeDatasourceEvidenceProjector.ELASTICSEARCH,
                "http://127.0.0.1:9200",
                Map.of("hits", 1));

        verify(store, never()).persist(any(), any(), any(), any());
    }

    @Test
    void explicitAgentScopeRuntimeContextProjectsWithoutThreadLocalTrace() {
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("run-agentscope")
                .projectId("project-1")
                .requestedBy("alice")
                .build();
        Map<String, Object> bounded = Map.of("adapter", "prometheus", "status", "SUCCEEDED");
        String outputHash = CanonicalObjectHasher.sha256(bounded);
        OpsAuthoritativeDatasourceEvidenceStore store = mock(OpsAuthoritativeDatasourceEvidenceStore.class);
        when(store.persist(eq(request), eq(OpsAuthoritativeDatasourceEvidenceProjector.PROMETHEUS),
                eq("LOCAL_PROMETHEUS"), any())).thenReturn(
                new OpsAuthoritativeDatasourceEvidenceStore.PersistedEvidence(
                        "tool-result-agent",
                        "evidence-agent",
                        outputHash,
                        "db:tool-result-agent",
                        bounded,
                        "2026-08-10 23:30:00"));
        OpsRuntimeResourceContext runtimeContext = OpsRuntimeResourceContext.builder()
                .agentScope(OpsAgentScopeConfig.builder().agentId("platform-ops-react").build())
                .events(events)
                .build();

        new OpsAuthoritativeDatasourceEvidenceProjector(store).record(
                request,
                OpsAuthoritativeDatasourceEvidenceProjector.PROMETHEUS,
                "LOCAL_PROMETHEUS",
                Map.of("adapter", "prometheus"),
                runtimeContext);

        assertEquals(1, events.size());
        OpsRuntimeEvent event = events.get(0);
        assertEquals("SOURCE_QUERY_FINISHED", event.getEventType());
        assertEquals("AGENTSCOPE", event.getNodeType());
        assertEquals("platform-ops-react", event.getAgent());
        assertEquals("tool-result-agent", event.getPayload().get("resultId"));
        assertEquals(outputHash, event.getPayload().get("outputHash"));
    }
}
