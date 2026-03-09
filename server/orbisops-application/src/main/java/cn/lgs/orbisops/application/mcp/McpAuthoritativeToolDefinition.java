package cn.lgs.orbisops.application.mcp;

public record McpAuthoritativeToolDefinition(
        String description,
        Object inputSchema,
        String schemaSource) {

    public McpAuthoritativeToolDefinition {
        description = text(description);
        schemaSource = text(schemaSource);
    }

    public static McpAuthoritativeToolDefinition empty() {
        return new McpAuthoritativeToolDefinition("", null, "");
    }

    public boolean hydrated() {
        return inputSchema != null;
    }

    public McpAuthoritativeToolDefinition persisted() {
        return new McpAuthoritativeToolDefinition(
                description, inputSchema, "PERSISTED_REMOTE_MCP_TOOL_DEFINITION");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
