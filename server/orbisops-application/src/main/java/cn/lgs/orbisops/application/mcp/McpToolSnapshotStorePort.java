package cn.lgs.orbisops.application.mcp;

import java.util.Optional;

public interface McpToolSnapshotStorePort {

    boolean available();

    void save(McpToolSchemaSnapshot snapshot);

    Optional<McpAuthoritativeToolDefinition> latestHydratedDefinition(
            String projectId,
            String mcpId,
            String toolName,
            int limit);

    Optional<McpReviewableToolSnapshot> latestReviewableSnapshot(
            String projectId,
            String mcpId,
            String toolId,
            String toolName);
}
