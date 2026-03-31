package cn.lgs.orbisops.application.mcp;

public record McpReviewableToolSnapshot(String schemaHash, boolean schemaHydrated) {

    public McpReviewableToolSnapshot {
        schemaHash = schemaHash == null ? "" : schemaHash.trim();
    }
}
