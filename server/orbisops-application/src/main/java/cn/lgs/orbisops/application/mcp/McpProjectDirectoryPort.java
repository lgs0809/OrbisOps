package cn.lgs.orbisops.application.mcp;

/** Project identity boundary consumed by MCP Governance. */
public interface McpProjectDirectoryPort {

    boolean exists(String projectId);
}
