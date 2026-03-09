package cn.lgs.orbisops.application.mcp;

public interface McpDiscoveryPort {

    McpDiscoverySelectionResult select(McpDiscoverySelectionRequest command);

    McpHydratedToolSchema hydrateSchema(McpSchemaHydrationRequest command);
}
