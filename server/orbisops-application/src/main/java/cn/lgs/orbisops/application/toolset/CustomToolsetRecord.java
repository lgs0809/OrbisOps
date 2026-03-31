package cn.lgs.orbisops.application.toolset;

import java.util.List;

/** Persistence-neutral custom Toolset aggregate snapshot. */
public record CustomToolsetRecord(
        String projectId,
        String toolsetId,
        String name,
        String description,
        String prerequisites,
        List<String> tags,
        String sourceType,
        String adapterType,
        boolean enabled,
        boolean readOnlyDefault,
        List<CustomToolDefinitionRecord> tools,
        String createBy,
        String updateBy,
        String createTime,
        String updateTime) {

    public CustomToolsetRecord {
        projectId = text(projectId);
        toolsetId = text(toolsetId);
        name = text(name);
        description = text(description);
        prerequisites = text(prerequisites);
        tags = tags == null ? List.of() : List.copyOf(tags);
        sourceType = text(sourceType);
        adapterType = text(adapterType);
        tools = tools == null ? List.of() : List.copyOf(tools);
        createBy = text(createBy);
        updateBy = text(updateBy);
        createTime = text(createTime);
        updateTime = text(updateTime);
    }

    public CustomToolsetRecord withEnabled(boolean enabled, String actor) {
        return new CustomToolsetRecord(
                projectId,
                toolsetId,
                name,
                description,
                prerequisites,
                tags,
                sourceType,
                adapterType,
                enabled,
                readOnlyDefault,
                tools,
                createBy,
                actor,
                createTime,
                updateTime);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
