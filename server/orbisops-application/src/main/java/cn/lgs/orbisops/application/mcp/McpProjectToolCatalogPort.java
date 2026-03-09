package cn.lgs.orbisops.application.mcp;

import java.util.List;
import java.util.Optional;

public interface McpProjectToolCatalogPort {

    List<McpProjectToolDescriptor> list(String projectId);

    Optional<McpProjectToolDescriptor> find(String projectId, String toolIdOrMcpId);
}
