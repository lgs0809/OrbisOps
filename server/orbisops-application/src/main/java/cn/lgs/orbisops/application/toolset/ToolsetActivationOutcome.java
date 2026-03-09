package cn.lgs.orbisops.application.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolsetDefinition;

import java.util.Map;

/** Typed result of enabling or disabling a custom Toolset. */
public record ToolsetActivationOutcome(
        String projectId,
        ToolsetDefinition toolset
) {

    public ToolsetActivationOutcome {
        projectId = required(projectId, "TOOLSET_PROJECT_ID_REQUIRED");
        if (toolset == null) throw new IllegalArgumentException("TOOLSET_DEFINITION_REQUIRED");
    }

    public Map<String, Object> view() {
        return Map.of(
                "projectId", projectId,
                "toolsetId", toolset.toolsetId(),
                "enabled", toolset.enabled());
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
