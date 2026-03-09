package cn.lgs.orbisops.domain.analysis.model;

public record AnalysisRunSnapshot(String runId,
                                  String projectId,
                                  String triggerSource,
                                  String status,
                                  String requestJson,
                                  String responseJson,
                                  String errorMessage,
                                  String createdAt,
                                  String updatedAt,
                                  Long durationMs) {

    public AnalysisRunSnapshot {
        runId = text(runId);
        projectId = text(projectId);
        triggerSource = text(triggerSource);
        status = text(status);
        requestJson = nullable(requestJson);
        responseJson = nullable(responseJson);
        errorMessage = nullable(errorMessage);
        createdAt = text(createdAt);
        updatedAt = text(updatedAt);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullable(String value) {
        return value == null ? null : value;
    }
}
