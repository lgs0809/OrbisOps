package cn.lgs.orbisops.domain.mcp.adapter.repository;

import cn.lgs.orbisops.domain.mcp.model.McpCatalogSummary;
import cn.lgs.orbisops.domain.mcp.model.McpRoutingDecision;
import cn.lgs.orbisops.domain.mcp.model.McpRuntimeActivation;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaCacheEntry;
import cn.lgs.orbisops.domain.mcp.model.McpToolCall;

import java.util.List;
import java.util.Optional;

public interface IMcpRuntimeCatalogRepository {

    boolean available();

    void saveCatalogSummary(McpCatalogSummary summary);

    void saveSchemaCache(McpSchemaCacheEntry schema);

    void saveActivation(McpRuntimeActivation activation);

    boolean isActivationActive(String projectId, String runId, String mcpId, String toolName);

    List<McpRuntimeActivation> findActivations(String projectId, int limit);

    void saveRoutingDecision(McpRoutingDecision decision);

    List<McpRoutingDecision> findRoutingDecisions(String projectId, int limit);

    void saveToolCall(McpToolCall call);

    List<McpToolCall> findToolCalls(String projectId, int limit);

    Optional<McpToolCall> findToolCall(String projectId, String callId);
}
