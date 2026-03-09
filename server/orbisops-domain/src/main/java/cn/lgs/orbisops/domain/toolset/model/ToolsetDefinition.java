package cn.lgs.orbisops.domain.toolset.model;

import java.util.List;

/** Immutable Toolset aggregate snapshot used by runtime selection and administration. */
public record ToolsetDefinition(
        String toolsetId,
        String name,
        String description,
        String prerequisites,
        List<String> tags,
        String sourceType,
        String adapterType,
        boolean enabled,
        boolean readOnlyDefault,
        List<ToolDefinition> tools,
        String createBy,
        String updateBy,
        String createTime,
        String updateTime
) {

    public ToolsetDefinition {
        toolsetId = required(toolsetId, "TOOLSET_ID_REQUIRED");
        name = fallback(name, toolsetId);
        description = text(description);
        prerequisites = text(prerequisites);
        tags = tags == null ? List.of() : List.copyOf(tags);
        sourceType = required(sourceType, "TOOLSET_SOURCE_TYPE_REQUIRED");
        adapterType = required(adapterType, "TOOLSET_ADAPTER_TYPE_REQUIRED");
        tools = tools == null ? List.of() : List.copyOf(tools);
        createBy = text(createBy);
        updateBy = text(updateBy);
        createTime = text(createTime);
        updateTime = text(updateTime);
    }

    public ToolsetDefinition withEnabled(boolean next, String actor) {
        return new ToolsetDefinition(
                toolsetId,
                name,
                description,
                prerequisites,
                tags,
                sourceType,
                adapterType,
                next,
                readOnlyDefault,
                tools,
                createBy,
                actor,
                createTime,
                updateTime);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String fallback(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
