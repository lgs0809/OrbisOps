package cn.lgs.orbisops.application.config;

import java.util.List;

/** Persistence boundary for MCP client catalog definitions. */
public interface McpClientCatalogPort {

    boolean insert(McpClientDefinition definition);

    boolean updateById(McpClientDefinition definition);

    boolean updateByMcpId(McpClientDefinition definition);

    boolean deleteById(Long id);

    boolean deleteByMcpId(String mcpId);

    McpClientDefinition findById(Long id);

    McpClientDefinition findByMcpId(String mcpId);

    List<McpClientDefinition> listAll();

    List<McpClientDefinition> listByStatus(Integer status);

    List<McpClientDefinition> listByTransportType(String transportType);

    List<McpClientDefinition> listEnabled();
}
