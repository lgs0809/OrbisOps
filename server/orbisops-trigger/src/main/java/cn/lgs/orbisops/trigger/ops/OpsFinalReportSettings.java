package cn.lgs.orbisops.trigger.ops;

/** Typed settings for final report generation and output bounding. */
public record OpsFinalReportSettings(boolean llmEnabled, int maxChars) {

    public OpsFinalReportSettings {
        maxChars = maxChars < 1_000 || maxChars > 100_000 ? 8_000 : maxChars;
    }

    public static OpsFinalReportSettings defaults() {
        return new OpsFinalReportSettings(true, 8_000);
    }
}
