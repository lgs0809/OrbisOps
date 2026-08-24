package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.tool.ToolCallback;
import java.util.ArrayList;
import java.util.HashSet;

/** Initialization and callback projection for one cached MCP client handle. */
final class OpsMcpClientHandleService {

    void initialize(OpsMcpClientRegistry.ClientHandle handle) {
        OpsMcpClientRegistry.ClientHandle cached = required(handle);
        OpsMcpRequestScope.acquire(cached.lock());
        try {
            cached.assertValid();
            if (!cached.initialized()) {
                try {
                    cached.client().initialize();
                    // Publish readiness only after Initialize succeeds, never while the handshake is in flight.
                    cached.markInitializing();
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
        OpsMcpRequestScope.acquire(cached.lock());
        try {
            cached.assertValid();
            cached.touch();
            var callbacks = new ArrayList<ToolCallback>();
            var cursors = new HashSet<String>();
            String cursor = null;
            do {
                var page = cursor == null ? cached.client().listTools() : cached.client().listTools(cursor);
                if (page == null || page.tools() == null) throw new IllegalStateException("MCP_TOOL_LIST_EMPTY_RESPONSE");
                for (var tool : page.tools()) callbacks.add(new OpsMcpFullResultToolCallback(cached, tool));
                cursor = page.nextCursor();
                if (cursor != null && !cursor.isBlank() && (!cursors.add(cursor) || cursors.size() > 64)) {
                    throw new IllegalStateException("MCP_TOOL_LIST_CURSOR_INVALID");
                }
            } while (cursor != null && !cursor.isBlank());
            return callbacks.toArray(ToolCallback[]::new);
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
