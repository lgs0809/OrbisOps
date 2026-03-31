package cn.lgs.orbisops.application.mcp;

public record McpAuthoritativeToolDefinition(
        String description,
        Object inputSchema,
        Object outputSchema,
        String schemaSource) {

    public McpAuthoritativeToolDefinition(String description, Object inputSchema, String schemaSource) {
        this(description, inputSchema, null, schemaSource);
    }

    public McpAuthoritativeToolDefinition {
        description = text(description);
        schemaSource = text(schemaSource);
        if (outputSchema instanceof java.util.Map<?, ?> schema && schema.isEmpty()) outputSchema = null;
    }

    public static McpAuthoritativeToolDefinition empty() {
        return new McpAuthoritativeToolDefinition("", null, "");
    }

    public boolean hydrated() {
        return inputSchema != null;
    }

    public McpAuthoritativeToolDefinition persisted() {
        return new McpAuthoritativeToolDefinition(
                description, inputSchema, outputSchema, "PERSISTED_REMOTE_MCP_TOOL_DEFINITION");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
