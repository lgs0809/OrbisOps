package cn.lgs.orbisops.domain.toolset.model;

/** Stable reference used by Workflow, Agent Capability and Skill manifests. */
public record ToolReference(
        String toolsetId,
        String toolName
) {

    public ToolReference {
        toolsetId = required(toolsetId, "TOOL_REFERENCE_TOOLSET_REQUIRED");
        toolName = required(toolName, "TOOL_REFERENCE_TOOL_REQUIRED");
    }

    public String canonicalId() {
        return toolsetId + "/" + toolName;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
