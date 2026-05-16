package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Builds the final user-facing operations report from collected evidence. */
@Service
public class OpsFinalReportService {

    private final OpsFinalReportLlmProtocolService llmProtocol;
    private final OpsFinalReportTrustService trustService;
    private final OpsFinalRuleReportRenderer ruleReportRenderer;
    private final OpsFinalReportSettings settings;

    public OpsFinalReportService(OpsAgentLlmClient llmClient) {
        this(llmClient, OpsFinalReportSettings.defaults());
    }

    @Autowired
    public OpsFinalReportService(
            OpsAgentLlmClient llmClient,
            OpsFinalReportSettings settings) {
        this(
                new OpsFinalReportLlmProtocolService(llmClient),
                new OpsFinalReportTrustService(),
                new OpsFinalRuleReportRenderer(),
                settings);
    }

    OpsFinalReportService(
            OpsAgentLlmClient llmClient,
            OpsFinalReportTrustService trustService) {
        this(
                new OpsFinalReportLlmProtocolService(llmClient),
                trustService,
                new OpsFinalRuleReportRenderer(),
                OpsFinalReportSettings.defaults());
    }

    OpsFinalReportService(
            OpsAgentLlmClient llmClient,
            OpsFinalReportTrustService trustService,
            OpsFinalRuleReportRenderer ruleReportRenderer) {
        this(
                new OpsFinalReportLlmProtocolService(llmClient),
                trustService,
                ruleReportRenderer,
                OpsFinalReportSettings.defaults());
    }

    OpsFinalReportService(
            OpsFinalReportLlmProtocolService llmProtocol,
            OpsFinalReportTrustService trustService,
            OpsFinalRuleReportRenderer ruleReportRenderer) {
        this(
                llmProtocol,
                trustService,
                ruleReportRenderer,
                OpsFinalReportSettings.defaults());
    }

    OpsFinalReportService(
            OpsFinalReportLlmProtocolService llmProtocol,
            OpsFinalReportTrustService trustService,
            OpsFinalRuleReportRenderer ruleReportRenderer,
            OpsFinalReportSettings settings) {
        this.llmProtocol = llmProtocol;
        this.trustService = trustService;
        this.ruleReportRenderer = ruleReportRenderer;
        this.settings = settings == null ? OpsFinalReportSettings.defaults() : settings;
    }

    public String buildFinalReport(
            OpsAnalysisResponseDTO response,
            String evidenceSnapshot) {
        if (settings.llmEnabled()) {
            String llmReport = buildLlmReport(response, evidenceSnapshot);
            if (!llmReport.isBlank()) {
                return llmReport;
            }
        }
        return buildRuleReport(response, evidenceSnapshot);
    }

    private String buildLlmReport(
            OpsAnalysisResponseDTO response,
            String evidenceSnapshot) {
        OpsFinalReportLlmProtocolService.Result result;
        try {
            result = llmProtocol.generate(
                    new OpsFinalReportLlmProtocolService.Input(
                            response.getAnalysisId(),
                            response.getGeneratedAt(),
                            response.getRangeMinutes(),
                            response.getPromWindow(),
                            evidenceSnapshot,
                            settings.maxChars()));
        } catch (OpsLlmDegradationException error) {
            appendReportGenerationNote(
                    response,
                    "LLM 最终报告生成失败，已降级到规则报告：" + error.getMessage());
            return "";
        }
        if (result.rejected()) {
            rejectReport(response, result.rejectionReason());
            return "";
        }
        if (!result.hasReport()) {
            return "";
        }
        String markdownReport = result.markdownReport();
        if (!trustService.trusted(response, markdownReport)) {
            rejectReport(
                    response,
                    "最终报告未通过证据边界检查，analysisId=" + response.getAnalysisId());
            appendReportGenerationNote(
                    response,
                    "LLM 最终报告未通过证据边界检查，已降级到规则报告。");
            return "";
        }
        appendReportGenerationNote(response, "最终报告由 LLM 基于真实证据合成。");
        return markdownReport;
    }

    private void rejectReport(
            OpsAnalysisResponseDTO response,
            String reason) {
        try {
            llmProtocol.rejectDegradation(reason);
        } catch (OpsLlmDegradationException error) {
            appendReportGenerationNote(
                    response,
                    "LLM 最终报告不可用，已降级到规则报告：" + error.getMessage());
        }
    }

    private String buildRuleReport(
            OpsAnalysisResponseDTO response,
            String evidenceSnapshot) {
        String report = ruleReportRenderer.render(
                response,
                evidenceSnapshot,
                settings.maxChars());
        appendReportGenerationNote(
                response,
                "最终报告由规则模板基于真实证据合成。");
        return report;
    }

    private void appendReportGenerationNote(
            OpsAnalysisResponseDTO response,
            String note) {
        if (response.getExecutionNotes() != null) {
            response.getExecutionNotes().add(note);
        }
    }
}
