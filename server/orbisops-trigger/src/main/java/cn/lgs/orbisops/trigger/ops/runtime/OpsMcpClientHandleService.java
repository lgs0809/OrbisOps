package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/** Initialization and callback projection for one cached MCP client handle. */
final class OpsMcpClientHandleService {

    void initialize(OpsMcpClientRegistry.ClientHandle handle) {
        OpsMcpClientRegistry.ClientHandle cached = required(handle);
        if (cached.initialized()) {
            cached.touch();
            return;
        }
        cached.lock().lock();
        try {
            if (cached.markInitializing()) {
                try {
                    cached.client().initialize();
                } catch (RuntimeException error) {
                    cached.resetInitialized();
                    throw error;
                }
            }
            cached.touch();
        } finally {
            cached.lock().unlock();
        }
    }

    ToolCallback[] toolCallbacks(OpsMcpClientRegistry.ClientHandle handle) {
        OpsMcpClientRegistry.ClientHandle cached = required(handle);
        cached.lock().lock();
        try {
            cached.touch();
            return new SyncMcpToolCallbackProvider(List.of(cached.client()))
                    .getToolCallbacks();
        } finally {
            cached.touch();
            cached.lock().unlock();
        }
    }

    private OpsMcpClientRegistry.ClientHandle required(
            OpsMcpClientRegistry.ClientHandle handle) {
        if (handle == null) {
            throw new IllegalArgumentException("MCP_CLIENT_HANDLE_REQUIRED");
        }
        return handle;
    }
}
