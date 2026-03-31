package cn.lgs.orbisops.application.toolset;

/** Persistence-neutral custom tool definition. */
public record CustomToolDefinitionRecord(
        String toolName,
        String displayName,
        String description,
        String parametersJson,
        String adapterType,
        String commandTemplate,
        String mcpServerId,
        String remoteToolName,
        String httpConfigJson,
        String dbConfigJson,
        boolean readOnly,
        boolean writesRepairWorkspace,
        boolean writesTargetResource,
        boolean requiresChangePackage,
        boolean requiresApproval,
        String riskLevel,
        String outputBudgetJson,
        boolean enabled) {

    public CustomToolDefinitionRecord {
        toolName = text(toolName);
        displayName = text(displayName);
        description = text(description);
        parametersJson = json(parametersJson);
        adapterType = text(adapterType);
        commandTemplate = text(commandTemplate);
        mcpServerId = text(mcpServerId);
        remoteToolName = text(remoteToolName);
        httpConfigJson = json(httpConfigJson);
        dbConfigJson = json(dbConfigJson);
        riskLevel = text(riskLevel);
        outputBudgetJson = json(outputBudgetJson);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String json(String value) {
        String text = text(value);
        return text.isBlank() ? "{}" : text;
    }
}
