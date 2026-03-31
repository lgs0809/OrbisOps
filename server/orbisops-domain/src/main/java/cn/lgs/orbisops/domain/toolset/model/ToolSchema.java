package cn.lgs.orbisops.domain.toolset.model;

/** Immutable Tool schema. Schema hydration remains a Provider responsibility. */
public record ToolSchema(
        String inputSchemaJson,
        String outputSchemaJson
) {

    public ToolSchema {
        inputSchemaJson = json(inputSchemaJson);
        outputSchemaJson = json(outputSchemaJson);
    }

    public static ToolSchema inputOnly(String inputSchemaJson) {
        return new ToolSchema(inputSchemaJson, "{}");
    }

    public static ToolSchema empty() {
        return new ToolSchema("{}", "{}");
    }

    private static String json(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? "{}" : normalized;
    }
}
