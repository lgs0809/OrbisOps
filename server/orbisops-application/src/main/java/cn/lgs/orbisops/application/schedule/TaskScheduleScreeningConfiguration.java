package cn.lgs.orbisops.application.schedule;

/** Explicit, machine-evaluable cheap-screening policy for one scheduled inspection. */
public record TaskScheduleScreeningConfiguration(
        boolean enabled,
        String sourceType,
        String primaryUri,
        Double maxErrorRatePercent,
        Double maxCpuPercent,
        Double maxHeapPercent,
        Double minInstanceUpRatio) {

    public TaskScheduleScreeningConfiguration {
        sourceType = text(sourceType).isBlank() ? "PROMETHEUS" : text(sourceType).toUpperCase();
        primaryUri = text(primaryUri);
        maxErrorRatePercent = nonNegative(maxErrorRatePercent);
        maxCpuPercent = boundedPercent(maxCpuPercent);
        maxHeapPercent = boundedPercent(maxHeapPercent);
        minInstanceUpRatio = ratio(minInstanceUpRatio);
    }

    public boolean hasBusinessCondition() {
        return maxErrorRatePercent != null
                || maxCpuPercent != null
                || maxHeapPercent != null
                || minInstanceUpRatio != null;
    }

    public static TaskScheduleScreeningConfiguration disabled() {
        return new TaskScheduleScreeningConfiguration(false, "PROMETHEUS", "", null, null, null, null);
    }

    private static Double nonNegative(Double value) {
        if (value == null) return null;
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0D) {
            throw new IllegalArgumentException("SCHEDULE_SCREENING_THRESHOLD_INVALID");
        }
        return value;
    }

    private static Double boundedPercent(Double value) {
        Double normalized = nonNegative(value);
        if (normalized != null && normalized > 100D) {
            throw new IllegalArgumentException("SCHEDULE_SCREENING_PERCENT_INVALID");
        }
        return normalized;
    }

    private static Double ratio(Double value) {
        Double normalized = nonNegative(value);
        if (normalized != null && normalized > 1D) {
            throw new IllegalArgumentException("SCHEDULE_SCREENING_RATIO_INVALID");
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
