package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.incident.IncidentDiagnosisQueryPort;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.incident.model.DiagnosisResult;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatIncidentPromotionServiceTest {

    @Test
    void actionRequiredStructuredDiagnosisCreatesRecurringIncidentAndLinksRun() {
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentDiagnosisQueryPort diagnoses = mock(IncidentDiagnosisQueryPort.class);
        GraphEventApplicationService graphEvents = mock(GraphEventApplicationService.class);
        IncidentSnapshot incident = incident("incident-chat");
        when(diagnoses.latestByRunIds(List.of("run-1"))).thenReturn(Optional.of(diagnosis(true, 1)));
        when(commands.openRecurring(any(), any(), eq("user-1"))).thenReturn(incident);
        OpsChatIncidentPromotionService service = new OpsChatIncidentPromotionService(commands, queries, diagnoses, graphEvents);
        OpsAgentChatRequest request = request(Map.of());
        OpsAgentChatResponse response = OpsAgentChatResponse.builder().metadata(Map.of()).build();

        Optional<String> result = service.promote(request, response);

        assertEquals(Optional.of("incident-chat"), result);
        verify(commands).linkRun("incident-chat", "run-1", "user-1", "结构化正式诊断自动归档到 Incident");
        ArgumentCaptor<String> recurringKey = ArgumentCaptor.forClass(String.class);
        verify(commands).openRecurring(recurringKey.capture(), any(), eq("user-1"));
        assertTrue(recurringKey.getValue().contains("session-1"));
        assertEquals("incident-chat", response.getMetadata().get("incidentId"));
        assertEquals(Boolean.TRUE, response.getMetadata().get("formalDiagnosisPromoted"));
        verify(graphEvents).publish(
                eq("run-1"),
                eq("session-1"),
                eq("INCIDENT_PROMOTION_FINISHED"),
                eq(null),
                eq("SUCCEEDED"),
                any(),
                eq(null),
                eq(null),
                eq(null),
                org.mockito.ArgumentMatchers.argThat(payload ->
                        "incident-chat".equals(payload.get("incidentId"))
                                && Boolean.TRUE.equals(payload.get("formalDiagnosisPromoted"))));
    }

    @Test
    void multiSourceEvidenceDiagnosisPromotesWithoutActionRequirement() {
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentDiagnosisQueryPort diagnoses = mock(IncidentDiagnosisQueryPort.class);
        when(diagnoses.latestByRunIds(List.of("run-1"))).thenReturn(Optional.of(diagnosis(false, 2)));
        when(commands.openRecurring(any(), any(), eq("user-1"))).thenReturn(incident("incident-chat"));
        OpsChatIncidentPromotionService service = new OpsChatIncidentPromotionService(commands, queries, diagnoses);

        Optional<String> result = service.promote(request(Map.of()), OpsAgentChatResponse.builder().metadata(Map.of()).build());

        assertTrue(result.isPresent());
    }

    @Test
    void simpleOrInsufficientAnswerDoesNotCreateIncident() {
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentDiagnosisQueryPort diagnoses = mock(IncidentDiagnosisQueryPort.class);
        when(diagnoses.latestByRunIds(List.of("run-1"))).thenReturn(Optional.of(DiagnosisResult.insufficient(
                "只完成一次简单查询", "没有正式诊断事实", false, "继续问答")));
        OpsChatIncidentPromotionService service = new OpsChatIncidentPromotionService(commands, queries, diagnoses);

        Optional<String> result = service.promote(request(Map.of()), OpsAgentChatResponse.builder().metadata(Map.of()).build());

        assertFalse(result.isPresent());
        verify(commands, never()).openRecurring(any(), any(), any());
    }

    @Test
    void chatRuntimeOutcomeWithVerifiedDatasourceEvidencePromotesWithoutAnalysisRepositoryRecord() {
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentDiagnosisQueryPort diagnoses = mock(IncidentDiagnosisQueryPort.class);
        when(diagnoses.latestByRunIds(List.of("run-1"))).thenReturn(Optional.empty());
        when(commands.openRecurring(any(), any(), eq("user-1"))).thenReturn(incident("incident-runtime"));
        OpsChatIncidentPromotionService service = new OpsChatIncidentPromotionService(commands, queries, diagnoses);
        OpsAgentChatResponse response = OpsAgentChatResponse.builder()
                .metadata(Map.of())
                .events(List.of(
                        authoritativeSource("PROMETHEUS", "result-prom", "hash-prom"),
                        authoritativeSource("ELASTICSEARCH", "result-es", "hash-es"),
                        OpsRuntimeEvent.builder()
                                .eventType("REACT_OUTCOME")
                                .status("SUCCEEDED")
                                .payload(Map.of(
                                        "requiresAction", true,
                                        "evidenceCompleteness", "PARTIAL",
                                        "verificationStatus", "INSUFFICIENT"))
                                .build()))
                .build();

        Optional<String> result = service.promote(request(Map.of()), response);

        assertEquals(Optional.of("incident-runtime"), result);
        assertEquals(Boolean.TRUE, response.getMetadata().get("formalDiagnosisPromoted"));
        verify(commands).linkRun("incident-runtime", "run-1", "user-1", "结构化正式诊断自动归档到 Incident");
    }

    @Test
    void runtimeOutcomeWithoutVerifiedDatasourceEvidenceMustNotPromoteIncident() {
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentDiagnosisQueryPort diagnoses = mock(IncidentDiagnosisQueryPort.class);
        when(diagnoses.latestByRunIds(List.of("run-1"))).thenReturn(Optional.empty());
        OpsChatIncidentPromotionService service = new OpsChatIncidentPromotionService(commands, queries, diagnoses);
        OpsAgentChatResponse response = OpsAgentChatResponse.builder()
                .metadata(Map.of())
                .events(List.of(OpsRuntimeEvent.builder()
                        .eventType("REACT_OUTCOME")
                        .status("SUCCEEDED")
                        .payload(Map.of(
                                "requiresAction", true,
                                "evidenceCompleteness", "INSUFFICIENT"))
                        .build()))
                .build();

        assertTrue(service.promote(request(Map.of()), response).isEmpty());
        verify(commands, never()).openRecurring(any(), any(), any());
    }

    @Test
    void existingIncidentOnlyLinksRunAndRequiresSameProject() {
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentDiagnosisQueryPort diagnoses = mock(IncidentDiagnosisQueryPort.class);
        when(queries.get("incident-existing")).thenReturn(Optional.of(incident("incident-existing")));
        OpsChatIncidentPromotionService service = new OpsChatIncidentPromotionService(commands, queries, diagnoses);
        OpsAgentChatResponse response = OpsAgentChatResponse.builder().metadata(Map.of()).build();

        Optional<String> result = service.promote(request(Map.of("incidentId", "incident-existing")), response);

        assertEquals(Optional.of("incident-existing"), result);
        verify(commands).linkRun("incident-existing", "run-1", "user-1", "Chat 继续调查关联分析 Run");
        verify(commands, never()).openRecurring(any(), any(), any());
        assertEquals(Boolean.FALSE, response.getMetadata().get("formalDiagnosisPromoted"));
    }

    private OpsAgentChatRequest request(Map<String, Object> metadata) {
        return OpsAgentChatRequest.builder()
                .userId("user-1")
                .sessionId("session-1")
                .runId("run-1")
                .projectId("project-a")
                .query("排查支付 5xx 的根因")
                .metadata(metadata)
                .build();
    }

    private IncidentSnapshot incident(String incidentId) {
        return new IncidentSnapshot(
                1L, incidentId, "project-a", "支付异常", IncidentStatus.OPEN, "WARNING", "payment", "CHAT",
                "", "", "run-1", "", "summary", "{}", "{}", 1L, "[]",
                "", "", "", "", "", "", "");
    }

    private DiagnosisResult diagnosis(boolean requiresAction, int successfulSources) {
        List<DiagnosisResult.SourceStatus> sources = java.util.stream.IntStream.range(0, successfulSources)
                .mapToObj(index -> new DiagnosisResult.SourceStatus(
                        "source-" + index,
                        "source-" + index,
                        DiagnosisResult.SourceQueryStatus.SUCCEEDED,
                        DiagnosisResult.SourceAssessment.UNKNOWN,
                        DiagnosisResult.SourceState.UNKNOWN,
                        "query ok"))
                .toList();
        return new DiagnosisResult(
                "已形成结构化诊断",
                List.of("支付失败率升高"),
                List.of(new DiagnosisResult.Fact(
                        "fact-1",
                        "错误率明显升高",
                        List.of(new DiagnosisResult.EvidenceRef("prometheus", "result-1", "hash-1")))),
                List.of(),
                List.of(),
                List.of(),
                List.of("继续处置"),
                sources,
                DiagnosisResult.EvidenceCompleteness.COMPLETE,
                DiagnosisResult.Confidence.HIGH,
                requiresAction,
                requiresAction ? "前往执行中心" : "继续观察");
    }

    private OpsRuntimeEvent authoritativeSource(String sourceType, String resultId, String outputHash) {
        return OpsRuntimeEvent.builder()
                .eventType("SOURCE_QUERY_FINISHED")
                .status("SUCCEEDED")
                .summary(sourceType + " query succeeded")
                .payload(Map.of(
                        "sourceType", sourceType,
                        "resultId", resultId,
                        "evidenceId", "evidence-" + sourceType.toLowerCase(),
                        "outputHash", outputHash,
                        "verified", true))
                .build();
    }
}
