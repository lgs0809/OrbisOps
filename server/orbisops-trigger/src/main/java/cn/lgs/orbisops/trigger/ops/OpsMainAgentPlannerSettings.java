package cn.lgs.orbisops.trigger.ops;

import java.util.Locale;
import java.util.Set;

/** Typed policy settings for main-agent planning mode and LLM participation. */
public record OpsMainAgentPlannerSettings(boolean llmEnabled, String mode) {

    private static final Set<String> ALL_SOURCES_MODES = Set.of(
            "all_sources", "all-sources", "all_tools", "all-tools");

    public OpsMainAgentPlannerSettings {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        mode = normalized.isBlank() ? "smart" : normalized;
    }

    public static OpsMainAgentPlannerSettings defaults() {
        return new OpsMainAgentPlannerSettings(true, "smart");
    }

    public boolean allSourcesMode() {
        return ALL_SOURCES_MODES.contains(mode);
    }
}
