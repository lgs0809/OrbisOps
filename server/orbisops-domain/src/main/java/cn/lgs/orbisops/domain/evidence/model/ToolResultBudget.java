package cn.lgs.orbisops.domain.evidence.model;

public record ToolResultBudget(
        int maxRows,
        int maxBytes,
        int maxLines,
        int maxPoints,
        int maxTimeRangeMinutes) {

    public ToolResultBudget {
        maxRows = positive(maxRows, 200);
        maxBytes = positive(maxBytes, 32 * 1024);
        maxLines = positive(maxLines, 400);
        maxPoints = positive(maxPoints, 1000);
        maxTimeRangeMinutes = positive(maxTimeRangeMinutes, 60);
    }

    public static ToolResultBudget defaults() {
        return new ToolResultBudget(200, 32 * 1024, 400, 1000, 60);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
