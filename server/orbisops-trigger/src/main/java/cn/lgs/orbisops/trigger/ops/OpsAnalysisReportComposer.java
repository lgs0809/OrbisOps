package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Composes operations analysis insights, evidence snapshots, final reports, and
 * prompts from the evidence collected by the graph runtime.
 */
@Service
public class OpsAnalysisReportComposer {

    private final OpsFinalReportService opsFinalReportService;
    private final OpsAnalysisInsightService insightService;
    private final OpsAnalysisEvidenceSnapshotRenderer snapshotRenderer;
    private final OpsAnalysisAiPromptBuilder promptBuilder;

    @org.springframework.beans.factory.annotation.Autowired
    public OpsAnalysisReportComposer(OpsFinalReportService opsFinalReportService) {
        this(
                opsFinalReportService,
                new OpsAnalysisInsightService(),
                new OpsAnalysisEvidenceSnapshotRenderer(),
                new OpsAnalysisAiPromptBuilder());
    }

    OpsAnalysisReportComposer(
            OpsFinalReportService opsFinalReportService,
            OpsAnalysisInsightService insightService) {
        this(
                opsFinalReportService,
                insightService,
                new OpsAnalysisEvidenceSnapshotRenderer(),
                new OpsAnalysisAiPromptBuilder());
    }

    OpsAnalysisReportComposer(
            OpsFinalReportService opsFinalReportService,
            OpsAnalysisInsightService insightService,
            OpsAnalysisEvidenceSnapshotRenderer snapshotRenderer) {
        this(
                opsFinalReportService,
                insightService,
                snapshotRenderer,
                new OpsAnalysisAiPromptBuilder());
    }

    OpsAnalysisReportComposer(
            OpsFinalReportService opsFinalReportService,
            OpsAnalysisInsightService insightService,
            OpsAnalysisEvidenceSnapshotRenderer snapshotRenderer,
            OpsAnalysisAiPromptBuilder promptBuilder) {
        this.opsFinalReportService = opsFinalReportService;
        this.insightService = insightService;
        this.snapshotRenderer = snapshotRenderer;
        this.promptBuilder = promptBuilder;
    }

    public void composeFinalReport(OpsAnalysisResponseDTO response) {
        response.setInsights(buildInsights(response));
        String evidenceSnapshot = buildDataSnapshot(response);
        response.setMarkdownReport(
                opsFinalReportService.buildFinalReport(response, evidenceSnapshot));
        response.setAiPrompt(buildAiPrompt(response));
    }

    public List<OpsAnalysisResponseDTO.InsightDTO> buildInsights(
            OpsAnalysisResponseDTO response) {
        return insightService.build(response);
    }

    public String buildDataSnapshot(OpsAnalysisResponseDTO response) {
        return snapshotRenderer.render(response);
    }

    public String buildAiPrompt(OpsAnalysisResponseDTO response) {
        return promptBuilder.build(response);
    }
}
