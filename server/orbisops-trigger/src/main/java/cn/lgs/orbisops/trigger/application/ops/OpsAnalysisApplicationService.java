package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.OpsEsLogSettings;
import cn.lgs.orbisops.trigger.ops.OpsPrometheusSettings;
import cn.lgs.orbisops.trigger.ops.OpsStructuredReportService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Thin application facade for normalized analysis execution and response composition. */
@Service
public class OpsAnalysisApplicationService {

    private final ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession;
    private final OpsStructuredReportService structuredReportService;
    private final OpsAnalysisRunRequestNormalizer requestNormalizer;
    private final OpsAnalysisRuntimeRequestFactory runtimeRequestFactory;
    private final OpsAnalysisResponseShellFactory responseShellFactory;
    private final OpsAnalysisRuntimeResponseProjector runtimeResponseProjector;

    public OpsAnalysisApplicationService(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
            OpsStructuredReportService structuredReportService,
            OpsAgentDefinitionQueryGateway agentDefinitions) {
        this(
                executeWorkSession,
                structuredReportService,
                agentDefinitions,
                OpsEsLogSettings.defaults(),
                OpsPrometheusSettings.defaults());
    }

    @Autowired
    public OpsAnalysisApplicationService(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> executeWorkSession,
            OpsStructuredReportService structuredReportService,
            OpsAgentDefinitionQueryGateway agentDefinitions,
            OpsEsLogSettings esLogSettings,
            OpsPrometheusSettings prometheusSettings) {
        OpsAnalysisAgentDefinitionSnapshotResolver snapshotResolver =
                new OpsAnalysisAgentDefinitionSnapshotResolver(agentDefinitions);
        this.executeWorkSession = executeWorkSession;
        this.structuredReportService = structuredReportService;
        this.requestNormalizer = new OpsAnalysisRunRequestNormalizer(snapshotResolver);
        this.runtimeRequestFactory = new OpsAnalysisRuntimeRequestFactory(snapshotResolver);
        this.responseShellFactory = new OpsAnalysisResponseShellFactory(esLogSettings, prometheusSettings);
        this.runtimeResponseProjector = new OpsAnalysisRuntimeResponseProjector();
    }

    public OpsAnalysisResponseDTO buildAnalysis(OpsAgentRunRequestDTO request) {
        OpsAnalysisResponseDTO response = responseShellFactory.create(request);
        OpsAgentChatResponse runtimeResponse = executeWorkSession.execute(
                runtimeRequestFactory.create(request, response));
        runtimeResponseProjector.project(request, response, runtimeResponse);
        response.setStructuredReport(structuredReportService.compose(response));
        return response;
    }

    public OpsAgentRunRequestDTO normalizeRequest(OpsAgentRunRequestDTO request) {
        return requestNormalizer.normalize(request);
    }
}
