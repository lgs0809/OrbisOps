package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.trigger.ops.OpsEsLogSettings;
import cn.lgs.orbisops.trigger.ops.OpsPrometheusSettings;
import cn.lgs.orbisops.trigger.ops.OpsStructuredReportService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAnalysisRuntimeMetadata;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpsAnalysisApplicationServiceTest {

    @SuppressWarnings("unchecked")
    private ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent>
    workSession() {
        return mock(ExecuteWorkSessionUseCase.class);
    }

    @Test
    public void shouldBuildAnalysisThroughGenericWorkSessionUseCase() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsStructuredReportService structuredReportService = mock(OpsStructuredReportService.class);
        Map<String, Object> structuredReport = Map.of("analysisId", "ops_unit");
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenReturn(OpsAgentChatResponse.builder()
                .agentId("generic-ops-react-agent")
                .agentVersion(1)
                .engine("HYBRID")
                .content("ok")
                .build());
        when(structuredReportService.compose(any(OpsAnalysisResponseDTO.class))).thenReturn(structuredReport);
        OpsAnalysisApplicationService service = new OpsAnalysisApplicationService(
                workSession,
                structuredReportService,
                mock(OpsAgentDefinitionQueryGateway.class));
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("ops-run-canonical")
                .projectId("payment")
                .question("支付接口 5xx 增多，帮我排查")
                .rangeMinutes(15)
                .promWindow("5m")
                .build();

        OpsAnalysisResponseDTO response = service.buildAnalysis(request);

        ArgumentCaptor<OpsAgentChatRequest> requestCaptor =
                ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(workSession).execute(requestCaptor.capture());
        OpsAgentChatRequest runtimeRequest = requestCaptor.getValue();
        assertEquals("AGENT", runtimeRequest.getMode());
        assertEquals(null, runtimeRequest.getEngine());
        assertEquals("支付接口 5xx 增多，帮我排查", runtimeRequest.getQuery());
        assertEquals("payment", runtimeRequest.getProjectId());
        assertEquals("ops-run-canonical", runtimeRequest.getRunId());
        assertEquals("ops-analysis", runtimeRequest.getMetadata().get("triggerSource"));
        assertSame(request, runtimeRequest.getMetadata().get(OpsAnalysisRuntimeMetadata.REQUEST_KEY));
        assertNotNull(runtimeRequest.getMetadata().get(OpsAnalysisRuntimeMetadata.RESPONSE_KEY));
        assertNotNull(runtimeRequest.getMetadata().get(OpsAnalysisRuntimeMetadata.QUESTION_CONTEXT_KEY));
        assertEquals("ok", response.getMarkdownReport());
        verify(structuredReportService).compose(same(response));
        assertSame(structuredReport, response.getStructuredReport());
    }

    @Test
    public void shouldFreezeAgentDefinitionSnapshotWhenNormalizingRunRequest() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsStructuredReportService structuredReportService = mock(OpsStructuredReportService.class);
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .version(7)
                .definitionHash("hash-v7")
                .engine("GRAPH")
                .name("Ops Agent")
                .queryRewriteEnabled(false)
                .build();
        when(registry.resolveForProject("ops-agent", null, false, "payment")).thenReturn(definition);
        when(registry.resolveForProject("ops-agent", 7, false, "payment")).thenReturn(definition);
        when(workSession.execute(any(OpsAgentChatRequest.class)))
                .thenReturn(OpsAgentChatResponse.builder().content("ok").build());
        when(structuredReportService.compose(any(OpsAnalysisResponseDTO.class)))
                .thenReturn(Map.of("analysisId", "ops_unit"));
        OpsAnalysisApplicationService service = new OpsAnalysisApplicationService(
                workSession,
                structuredReportService,
                registry);

        OpsAgentRunRequestDTO normalized = service.normalizeRequest(OpsAgentRunRequestDTO.builder()
                .projectId("payment")
                .requestedBy("alice")
                .agentDefinitionId("ops-agent")
                .question("继续查这个接口")
                .build());
        service.buildAnalysis(normalized);

        assertEquals("ops-agent", normalized.getAgentDefinitionId());
        assertEquals("alice", normalized.getRequestedBy());
        assertEquals(7, normalized.getAgentVersion());
        assertNotNull(normalized.getAgentDefinitionSnapshotJson());
        assertEquals(Boolean.FALSE,
                JSON.parseObject(normalized.getAgentDefinitionSnapshotJson(), OpsAgentDefinition.class)
                        .getQueryRewriteEnabled());
        ArgumentCaptor<OpsAgentChatRequest> requestCaptor =
                ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(workSession).execute(requestCaptor.capture());
        assertEquals("alice", requestCaptor.getValue().getUserId());
        assertEquals(Boolean.FALSE,
                requestCaptor.getValue().getAgentDefinition().getQueryRewriteEnabled());
    }

    @Test
    public void shouldRejectForgedAgentDefinitionSnapshot() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsStructuredReportService structuredReportService = mock(OpsStructuredReportService.class);
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAgentDefinition authoritative = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .projectId("payment")
                .version(7)
                .definitionHash("authoritative-hash")
                .engine("GRAPH")
                .name("Ops Agent")
                .build();
        when(registry.resolveForProject("ops-agent", 7, false, "payment"))
                .thenReturn(authoritative);
        OpsAnalysisApplicationService service = new OpsAnalysisApplicationService(
                workSession,
                structuredReportService,
                registry);
        OpsAgentDefinition forged = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .projectId("payment")
                .version(7)
                .definitionHash("request-forged-hash")
                .engine("GRAPH")
                .name("Ops Agent")
                .build();

        assertThrows(SecurityException.class, () -> service.normalizeRequest(
                OpsAgentRunRequestDTO.builder()
                        .projectId("payment")
                        .agentDefinitionId("ops-agent")
                        .agentVersion(7)
                        .agentDefinitionSnapshotJson(JSON.toJSONString(forged))
                        .question("继续排查")
                        .build()));
    }

    @Test
    public void shouldPreserveReportComposedInsideGraphRuntime() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsStructuredReportService structuredReportService = mock(OpsStructuredReportService.class);
        doAnswer(invocation -> {
            OpsAgentChatRequest runtimeRequest = invocation.getArgument(0);
            OpsAnalysisResponseDTO response = (OpsAnalysisResponseDTO) runtimeRequest.getMetadata()
                    .get(OpsAnalysisRuntimeMetadata.RESPONSE_KEY);
            response.setMarkdownReport("正式证据报告");
            return OpsAgentChatResponse.builder()
                    .agentId("ops-agent")
                    .engine("GRAPH")
                    .content("最后一个调度节点的临时输出")
                    .build();
        }).when(workSession).execute(any(OpsAgentChatRequest.class));
        when(structuredReportService.compose(any(OpsAnalysisResponseDTO.class))).thenReturn(Map.of());
        OpsAnalysisApplicationService service = new OpsAnalysisApplicationService(
                workSession,
                structuredReportService,
                mock(OpsAgentDefinitionQueryGateway.class));

        OpsAnalysisResponseDTO response = service.buildAnalysis(OpsAgentRunRequestDTO.builder()
                .question("检查实例状态")
                .build());

        assertEquals("正式证据报告", response.getMarkdownReport());
    }

    @Test
    public void shouldUseInjectedTypedDatasourceSettingsInPublicResponseShell() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsStructuredReportService structuredReportService = mock(OpsStructuredReportService.class);
        when(workSession.execute(any(OpsAgentChatRequest.class)))
                .thenReturn(OpsAgentChatResponse.builder().content("ok").build());
        when(structuredReportService.compose(any(OpsAnalysisResponseDTO.class))).thenReturn(Map.of());
        OpsAnalysisApplicationService service = new OpsAnalysisApplicationService(
                workSession,
                structuredReportService,
                mock(OpsAgentDefinitionQueryGateway.class),
                new OpsEsLogSettings("http://es:9200/", "logs-*", 5, 8),
                new OpsPrometheusSettings("http://prom:9090/", "demo-service", 5));

        OpsAnalysisResponseDTO response = service.buildAnalysis(OpsAgentRunRequestDTO.builder()
                .question("检查状态")
                .build());

        assertEquals("http://es:9200/logs-*", response.getElasticsearchStatus().getUrl());
        assertEquals("http://prom:9090", response.getPrometheusStatus().getUrl());
    }
}
