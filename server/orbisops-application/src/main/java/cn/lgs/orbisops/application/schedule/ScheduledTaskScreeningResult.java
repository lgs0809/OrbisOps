package cn.lgs.orbisops.application.schedule;

import java.util.List;

/** Result of the cheap read-only check that precedes deep scheduled investigation. */
public record ScheduledTaskScreeningResult(
        Status status,
        String summary,
        List<String> abnormalSources) {

    public ScheduledTaskScreeningResult {
        status = status == null ? Status.INCONCLUSIVE : status;
        summary = summary == null ? "" : summary.trim();
        abnormalSources = abnormalSources == null ? List.of() : List.copyOf(abnormalSources);
    }

    /** Backward-compatible constructor for existing adapters/tests. */
    public ScheduledTaskScreeningResult(boolean normal, String summary, List<String> abnormalSources) {
        this(normal ? Status.NORMAL : Status.ABNORMAL, summary, abnormalSources);
    }

    public boolean normal() {
        return status == Status.NORMAL;
    }

    public static ScheduledTaskScreeningResult normal(String summary) {
        return new ScheduledTaskScreeningResult(Status.NORMAL, summary, List.of());
    }

    public static ScheduledTaskScreeningResult abnormal(String summary, List<String> abnormalSources) {
        return new ScheduledTaskScreeningResult(Status.ABNORMAL, summary, abnormalSources);
    }

    public static ScheduledTaskScreeningResult deepRequired(String reason) {
        return new ScheduledTaskScreeningResult(Status.INCONCLUSIVE, reason, List.of());
    }

    public static ScheduledTaskScreeningResult configError(String reason) {
        return new ScheduledTaskScreeningResult(Status.CONFIG_ERROR, reason, List.of());
    }

    public enum Status {
        NORMAL,
        ABNORMAL,
        INCONCLUSIVE,
        CONFIG_ERROR
    }
}
