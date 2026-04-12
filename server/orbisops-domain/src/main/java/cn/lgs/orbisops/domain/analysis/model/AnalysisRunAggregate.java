package cn.lgs.orbisops.domain.analysis.model;

public final class AnalysisRunAggregate {

    private final String runId;
    private AnalysisRunStatus status;

    private AnalysisRunAggregate(String runId, AnalysisRunStatus status) {
        this.runId = required(runId, "ANALYSIS_RUN_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("ANALYSIS_RUN_STATUS_REQUIRED");
        this.status = status;
    }

    public static AnalysisRunAggregate rehydrate(String runId, AnalysisRunStatus status) {
        return new AnalysisRunAggregate(runId, status);
    }

    public void requireCancelable() {
        if (status.terminal()) {
            throw new IllegalStateException("ANALYSIS_RUN_CANCEL_FORBIDDEN:" + status.name());
        }
    }

    public AnalysisRunStatus cancel() {
        requireCancelable();
        status = AnalysisRunStatus.CANCELED;
        return status;
    }

    public String runId() { return runId; }
    public AnalysisRunStatus status() { return status; }

    private static String required(String input, String error) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }
}
