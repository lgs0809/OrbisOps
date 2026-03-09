package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import lombok.extern.slf4j.Slf4j;

/** Projects terminal analysis outcomes back to the typed alert event application boundary. */
@Slf4j
final class OpsAnalysisRunAlertOutcomeReporter {

    private final AlertEventApplicationService alertEvents;

    OpsAnalysisRunAlertOutcomeReporter(AlertEventApplicationService alertEvents) {
        this.alertEvents = alertEvents;
    }

    void report(
            OpsAgentRunRequestDTO request,
            String runId,
            String status,
            String finalSummary,
            String errorMessage) {
        if (request == null || alertEvents == null) {
            return;
        }
        try {
            alertEvents.updateRunOutcome(new AlertRunOutcome(
                    request.getTriggerSource(),
                    request.getTriggerEventId(),
                    runId,
                    status,
                    finalSummary,
                    errorMessage));
        } catch (Exception error) {
            log.warn("反写告警触发事件状态失败，runId={}，error={}", runId, error.getMessage());
        }
    }

    String summarize(OpsAnalysisResponseDTO response) {
        if (response == null || response.getMarkdownReport() == null
                || response.getMarkdownReport().isBlank()) {
            return "";
        }
        String normalized = response.getMarkdownReport().replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
