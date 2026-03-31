package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpAuthoritativeToolDefinition;
import cn.lgs.orbisops.application.mcp.McpJsonCodec;
import cn.lgs.orbisops.application.mcp.McpReviewableToolSnapshot;
import cn.lgs.orbisops.application.mcp.McpToolSchemaSnapshot;
import cn.lgs.orbisops.application.mcp.McpToolSnapshotStorePort;
import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolSnapshotRepository;
import cn.lgs.orbisops.domain.mcp.model.McpToolSnapshot;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public final class OpsMcpToolSnapshotStoreAdapter implements McpToolSnapshotStorePort {

    private final IMcpToolSnapshotRepository repository;
    private final McpJsonCodec json = new McpJsonCodec();

    public OpsMcpToolSnapshotStoreAdapter(IMcpToolSnapshotRepository repository) {
        if (repository == null) throw new IllegalArgumentException("MCP_TOOL_SNAPSHOT_REPOSITORY_REQUIRED");
        this.repository = repository;
    }

    @Override
    public boolean available() {
        return repository.available();
    }

    @Override
    public void save(McpToolSchemaSnapshot snapshot) {
        requireAvailable();
        repository.save(new McpToolSnapshot(
                snapshot.snapshotId(),
                snapshot.projectId(),
                snapshot.mcpId(),
                snapshot.toolId(),
                snapshot.toolName(),
                snapshot.schemaHash(),
                json.encode(snapshot.schema().view()),
                json.encode(snapshot.rawMetadata()),
                snapshot.metadataComplete(),
                "ACTIVE",
                null,
                null));
    }

    @Override
    public Optional<McpAuthoritativeToolDefinition> latestHydratedDefinition(
            String projectId,
            String mcpId,
            String toolName,
            int limit) {
        if (!available()) return Optional.empty();
        return repository.findRecentActive(projectId, mcpId, toolName, limit).stream()
                .map(McpToolSnapshot::schemaJson)
                .map(this::map)
                .filter(schema -> bool(schema.get("schemaHydrated")) && schema.get("schema") != null)
                .findFirst()
                .map(schema -> new McpAuthoritativeToolDefinition(
                        text(schema.get("description")),
                        schema.get("schema"),
                        schema.get("outputSchema"),
                        "PERSISTED_REMOTE_MCP_TOOL_DEFINITION"));
    }

    @Override
    public Optional<McpReviewableToolSnapshot> latestReviewableSnapshot(
            String projectId,
            String mcpId,
            String toolId,
            String toolName) {
        if (!available()) return Optional.empty();
        return repository.findLatestActive(projectId, mcpId, toolId, toolName)
                .map(snapshot -> new McpReviewableToolSnapshot(
                        snapshot.schemaHash(),
                        bool(map(snapshot.schemaJson()).get("schemaHydrated"))));
    }

    private void requireAvailable() {
        if (!available()) throw new IllegalStateException("MCP_TOOL_SNAPSHOT_STORE_UNAVAILABLE");
    }

    private Map<String, Object> map(String encoded) {
        if (encoded == null || encoded.isBlank()) return Map.of();
        try {
            Object decoded = json.decode(encoded);
            if (!(decoded instanceof Map<?, ?> source)) return Map.of();
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(text(value)) || "1".equals(text(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
