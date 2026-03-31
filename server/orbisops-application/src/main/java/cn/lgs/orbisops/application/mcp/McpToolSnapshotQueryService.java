package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolSnapshotRepository;

import java.util.List;
import java.util.Map;

/** Query boundary for persisted MCP tool advertisement/schema snapshots. */
public final class McpToolSnapshotQueryService {

    private final McpProjectDirectoryPort projects;
    private final IMcpToolSnapshotRepository snapshots;
    private final McpRuntimeViewMapper views;

    public McpToolSnapshotQueryService(
            McpProjectDirectoryPort projects,
            IMcpToolSnapshotRepository snapshots,
            McpRuntimeViewMapper views) {
        if (projects == null) throw new IllegalArgumentException("MCP_PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (snapshots == null) throw new IllegalArgumentException("MCP_TOOL_SNAPSHOT_REPOSITORY_REQUIRED");
        if (views == null) throw new IllegalArgumentException("MCP_RUNTIME_VIEW_MAPPER_REQUIRED");
        this.projects = projects;
        this.snapshots = snapshots;
        this.views = views;
    }

    public List<Map<String, Object>> snapshots(String projectId, int limit) {
        String id = project(projectId);
        int max = limit(limit);
        if (!snapshots.available()) return List.of();
        return snapshots.findAll(id, max).stream().map(views::snapshotView).toList();
    }

    private String project(String value) {
        String normalized = required(value, "MCP_PROJECT_ID_REQUIRED");
        if (!projects.exists(normalized)) throw new IllegalArgumentException("项目不存在：" + normalized);
        return normalized;
    }

    private int limit(int value) {
        if (value <= 0 || value > 1000) throw new IllegalArgumentException("MCP_QUERY_LIMIT_INVALID");
        return value;
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
