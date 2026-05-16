package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisRuntimeResponseProjectorTest {

    private final OpsAnalysisRuntimeResponseProjector projector =
            new OpsAnalysisRuntimeResponseProjector();

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"WAITING_APPROVAL", "CANCELED", "FAILED", "SUCCEEDED"})
    void retainsRuntimeStatusInsteadOfInferringSuccessFromReport(String status) {
        var response = new OpsAnalysisResponseDTO();
        projector.project(new OpsAgentRunRequestDTO(), response,
                OpsAgentChatResponse.builder().content("report exists")
                        .metadata(Map.of("status", status)).build());
        assertEquals(status, response.getRuntimeStatus());
    }

    @Test
    void shouldProjectRuntimeIdentityEventsAndMarkdownFallback() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .nodeId("node-1")
                .nodeType("agent")
                .agent("log-agent")
                .source("elasticsearch")
                .status("SUCCESS")
                .summary("found logs")
                .timestamp("2026-07-28 17:00:00")
                .payload(Map.of(
                        "durationMs", 123L,
                        "sourceType", "ELASTICSEARCH",
                        "resultId", "tool-result-1",
                        "evidenceId", "evidence-1",
                        "outputHash", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "verified", true))
                .build();
        OpsRuntimeEvent ignored = OpsRuntimeEvent.builder()
                .nodeId(null)
                .summary("ignored")
                .build();

        projector.project(
                OpsAgentRunRequestDTO.builder().query("fallback query").build(),
                response,
                OpsAgentChatResponse.builder()
                        .agentId("ops-agent")
                        .agentVersion(7)
                        .engine("GRAPH")
                        .content("runtime report")
                        .events(List.of(event, ignored))
                        .build());

        assertAll(
                () -> assertEquals("SUCCEEDED", response.getRuntimeStatus()),
                () -> assertEquals("ops-agent", response.getAgentDefinitionId()),
                () -> assertEquals(7, response.getAgentVersion()),
                () -> assertEquals("GRAPH", response.getAgentRuntime()),
                () -> assertEquals("fallback query", response.getAiPrompt()),
                () -> assertEquals("runtime report", response.getMarkdownReport()),
                () -> assertEquals(1, response.getAgentExecutionSteps().size()),
                () -> assertEquals("node-1", response.getAgentExecutionSteps().get(0).getNodeId()),
                () -> assertEquals("ELASTICSEARCH", response.getAgentExecutionSteps().get(0).getSourceType()),
                () -> assertEquals("tool-result-1", response.getAgentExecutionSteps().get(0).getResultId()),
                () -> assertEquals("evidence-1", response.getAgentExecutionSteps().get(0).getEvidenceId()),
                () -> assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", response.getAgentExecutionSteps().get(0).getOutputHash()),
                () -> assertTrue(response.getAgentExecutionSteps().get(0).getVerified()),
                () -> assertEquals(123L, response.getAgentExecutionSteps().get(0).getDurationMs()),
                () -> assertEquals(1, response.getExecutionNotes().size()));
    }

    @Test
    void shouldProjectNodeLessSkillAndRagSemanticEvents() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        OpsRuntimeEvent skill = OpsRuntimeEvent.builder()
                .eventType("SKILL_CONTEXT_LOADED")
                .nodeType("SKILL_CONTEXT")
                .agent("demo-ops")
                .status("SUCCEEDED")
                .summary("skill loaded")
                .build();
        OpsRuntimeEvent rag = OpsRuntimeEvent.builder()
                .eventType("RAG_RETRIEVE")
                .status("NOT_FOUND")
                .summary("rag attempted")
                .build();

        projector.project(
                OpsAgentRunRequestDTO.builder().query("query").build(),
                response,
                OpsAgentChatResponse.builder().events(List.of(skill, rag)).build());

        assertEquals(2, response.getAgentExecutionSteps().size());
        assertEquals("SKILL_CONTEXT_LOADED", response.getAgentExecutionSteps().get(0).getEventType());
        assertEquals("demo-ops", response.getAgentExecutionSteps().get(0).getAgent());
        assertEquals("RAG_RETRIEVE", response.getAgentExecutionSteps().get(1).getEventType());
        assertEquals("RAG", response.getAgentExecutionSteps().get(1).getSourceType());
    }

    @Test
    void shouldPreserveGraphReportAndHandleMissingEventsAndDuration() {
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .markdownReport("正式证据报告")
                .build();

        projector.project(
                OpsAgentRunRequestDTO.builder().question("正式问题").build(),
                response,
                OpsAgentChatResponse.builder()
                        .content("临时节点输出")
                        .events(List.of(OpsRuntimeEvent.builder()
                                .nodeId("node-2")
                                .payload(Map.of("durationMs", "not-number"))
                                .build()))
                        .build());

        assertAll(
                () -> assertEquals("正式证据报告", response.getMarkdownReport()),
                () -> assertEquals("正式问题", response.getAiPrompt()),
                () -> assertEquals(1, response.getAgentExecutionSteps().size()),
                () -> assertNull(response.getAgentExecutionSteps().get(0).getDurationMs()));

        OpsAnalysisResponseDTO noEvents = new OpsAnalysisResponseDTO();
        projector.project(
                new OpsAgentRunRequestDTO(),
                noEvents,
                OpsAgentChatResponse.builder().events(null).build());
        assertTrue(noEvents.getAgentExecutionSteps().isEmpty());
    }
}
