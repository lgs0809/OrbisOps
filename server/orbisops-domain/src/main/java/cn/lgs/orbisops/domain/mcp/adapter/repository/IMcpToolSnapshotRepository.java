package cn.lgs.orbisops.domain.mcp.adapter.repository;

import cn.lgs.orbisops.domain.mcp.model.McpToolSnapshot;

import java.util.List;
import java.util.Optional;

public interface IMcpToolSnapshotRepository {

    boolean available();

    void save(McpToolSnapshot snapshot);

    List<McpToolSnapshot> findAll(String projectId, int limit);

    List<McpToolSnapshot> findRecentActive(String projectId, String mcpId, String toolName, int limit);

    Optional<McpToolSnapshot> findLatestActive(String projectId, String mcpId, String toolId, String toolName);
}
