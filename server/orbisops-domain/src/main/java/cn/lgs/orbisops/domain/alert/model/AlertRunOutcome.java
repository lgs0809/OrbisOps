package cn.lgs.orbisops.domain.alert.model;

public record AlertRunOutcome(
        String sourceType,
        String triggerEventId,
        String runId,
        String runStatus,
        String finalSummary,
        String errorMessage) {

    public AlertRunOutcome {
        sourceType = text(sourceType);
        triggerEventId = text(triggerEventId);
        runId = text(runId);
        runStatus = text(runStatus);
        finalSummary = text(finalSummary);
        errorMessage = text(errorMessage);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
