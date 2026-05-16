package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisOutcomePort;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

/** Graph event and alert outcome adapter for asynchronous run terminal states. */
public final class OpsAsyncAnalysisOutcomeAdapter implements
        AsyncAnalysisOutcomePort<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> {

    private static final String CANCELED_MESSAGE = "运维分析任务已取消。";

    private final GraphEventApplicationService graphEvents;
    private final OpsAnalysisRunAlertOutcomeReporter alertReporter;

    public OpsAsyncAnalysisOutcomeAdapter(
            GraphEventApplicationService graphEvents,
            OpsAnalysisRunAlertOutcomeReporter alertReporter) {
        if (graphEvents == null) {
            throw new IllegalArgumentException("OPS_GRAPH_EVENT_SERVICE_REQUIRED");
        }
        if (alertReporter == null) {
            throw new IllegalArgumentException("OPS_ALERT_OUTCOME_REPORTER_REQUIRED");
        }
        this.graphEvents = graphEvents;
        this.alertReporter = alertReporter;
    }

    @Override
    public void succeeded(
            OpsAgentRunRequestDTO request,
            String runId,
            OpsAnalysisResponseDTO response) {
        alertReporter.report(
                request,
                runId,
                OpsAnalysisRunStatus.SUCCEEDED,
                alertReporter.summarize(response),
                null);
    }

    @Override
    public void failed(OpsAgentRunRequestDTO request, String runId, String errorMessage) {
        alertReporter.report(
                request,
                runId,
                OpsAnalysisRunStatus.FAILED,
                null,
                errorMessage);
    }

    @Override
    public void canceled(OpsAgentRunRequestDTO request, String runId) {
        graphEvents.publishRunEvent(
                runId,
                runId,
                "RUN_FINISHED",
                OpsAnalysisRunStatus.CANCELED,
                CANCELED_MESSAGE);
        alertReporter.report(
                request,
                runId,
                OpsAnalysisRunStatus.CANCELED,
                null,
                CANCELED_MESSAGE);
    }
}
