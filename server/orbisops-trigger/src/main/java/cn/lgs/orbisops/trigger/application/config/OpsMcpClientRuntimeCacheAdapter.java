package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.McpClientRuntimeCachePort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;

/** Optional runtime MCP cache invalidation adapter. */
public final class OpsMcpClientRuntimeCacheAdapter implements McpClientRuntimeCachePort {

    private final OpsMcpToolProvider toolProvider;

    public OpsMcpClientRuntimeCacheAdapter(OpsMcpToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    @Override
    public void invalidateAll() {
        if (toolProvider != null) {
            toolProvider.invalidateAll();
        }
    }
}
