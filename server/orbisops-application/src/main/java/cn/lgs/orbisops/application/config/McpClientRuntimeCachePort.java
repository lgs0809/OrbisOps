package cn.lgs.orbisops.application.config;

/** Runtime boundary for invalidating cached MCP clients after catalog mutation. */
public interface McpClientRuntimeCachePort {

    void invalidateAll();
}
