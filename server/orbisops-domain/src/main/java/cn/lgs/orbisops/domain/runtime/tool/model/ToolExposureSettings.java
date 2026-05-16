package cn.lgs.orbisops.domain.runtime.tool.model;

import java.util.Locale;

/** Safety settings that define which runtime tools may be exposed to the model. */
public record ToolExposureSettings(
        String actionMode,
        boolean enforceReadOnlyTools) {

    public static final String DEFAULT_ACTION_MODE = "analysis-only";

    public ToolExposureSettings {
        actionMode = normalizeMode(actionMode);
    }

    public static ToolExposureSettings fromRaw(
            String actionMode,
            boolean enforceReadOnlyTools) {
        return new ToolExposureSettings(actionMode, enforceReadOnlyTools);
    }

    public static ToolExposureSettings defaults() {
        return new ToolExposureSettings(DEFAULT_ACTION_MODE, true);
    }

    public boolean analysisOnly() {
        return DEFAULT_ACTION_MODE.equals(actionMode)
                || "readonly".equals(actionMode)
                || "read-only".equals(actionMode);
    }

    private static String normalizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? DEFAULT_ACTION_MODE : normalized;
    }
}
