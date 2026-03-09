package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns remote MCP client sessions, callback snapshots, lookup and schema projection. */
public final class OpsMcpRemoteClientAdapter {

    private final OpsMcpClientRegistry clientRegistry;
    private final OpsMcpClientFactory clientFactory;

    public OpsMcpRemoteClientAdapter(
            OpsMcpClientRegistry clientRegistry,
            OpsMcpClientFactory clientFactory) {
        if (clientRegistry == null) {
            throw new IllegalArgumentException("MCP_CLIENT_REGISTRY_REQUIRED");
        }
        if (clientFactory == null) {
            throw new IllegalArgumentException("MCP_CLIENT_FACTORY_REQUIRED");
        }
        this.clientRegistry = clientRegistry;
        this.clientFactory = clientFactory;
    }

    public Session open(OpsMcpServerConfig config) {
        OpsMcpClientRegistry.ClientHandle handle = clientRegistry.acquire(
                clientFactory.cacheKey(config),
                config,
                () -> clientFactory.create(config));
        clientRegistry.initialize(handle);
        return new Session(handle, clientRegistry.toolCallbacks(handle));
    }

    public ToolCallback find(OpsMcpServerConfig config, String remoteToolName) {
        return find(open(config), remoteToolName);
    }

    public ToolCallback find(Session session, String remoteToolName) {
        if (session == null) {
            throw new IllegalArgumentException("MCP_REMOTE_CLIENT_SESSION_REQUIRED");
        }
        for (ToolCallback callback : session.callbacks()) {
            if (value(remoteToolName).equals(toolName(callback))) {
                return callback;
            }
        }
        throw new IllegalArgumentException("远端 MCP 工具不存在或未暴露：" + value(remoteToolName));
    }

    public Map<String, Object> inspectDefinition(
            OpsMcpServerConfig config,
            String remoteToolName) {
        if (config == null || !StringUtils.hasText(remoteToolName)) {
            throw new IllegalArgumentException("MCP schema discovery 必须提供配置和远端工具名");
        }
        ToolDefinition definition = find(config, remoteToolName).getToolDefinition();
        if (definition == null || !StringUtils.hasText(definition.inputSchema())) {
            throw new IllegalStateException(
                    "MCP_TOOL_SCHEMA_NOT_HYDRATED：远端工具没有可验证的 input schema，tool="
                            + remoteToolName);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolName", remoteToolName);
        result.put("description", value(definition.description()));
        result.put("inputSchema", parseSchema(definition.inputSchema(), remoteToolName));
        result.put("schemaSource", "REMOTE_MCP_TOOL_DEFINITION");
        return result;
    }

    public List<Map<String, Object>> inspectDefinitions(OpsMcpServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MCP discovery 必须提供后端登记的 Server 配置");
        }
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (ToolCallback callback : open(config).callbacks()) {
            ToolDefinition definition;
            try {
                definition = callback == null ? null : callback.getToolDefinition();
            } catch (RuntimeException malformedCallback) {
                continue;
            }
            if (definition == null
                    || !StringUtils.hasText(definition.name())
                    || !StringUtils.hasText(definition.inputSchema())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("toolName", value(definition.name()));
            item.put("description", value(definition.description()));
            item.put("inputSchema", parseSchema(definition.inputSchema(), definition.name()));
            item.put("schemaSource", "REMOTE_MCP_TOOL_DEFINITION");
            definitions.add(item);
        }
        if (definitions.isEmpty()) {
            throw new IllegalStateException(
                    "MCP_TOOL_DISCOVERY_EMPTY：远端 Server 未返回带有效 input schema 的工具");
        }
        return List.copyOf(definitions);
    }

    public void invalidateAll() {
        clientRegistry.invalidateAll();
    }

    private Object parseSchema(String schema, String toolName) {
        try {
            return JSON.parse(schema);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "MCP_TOOL_SCHEMA_INVALID：远端工具 input schema 不是有效 JSON，tool="
                            + value(toolName),
                    error);
        }
    }

    private String toolName(ToolCallback callback) {
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        return definition == null ? "" : value(definition.name());
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Session(OpsMcpClientRegistry.ClientHandle handle, ToolCallback[] callbacks) {
        public Session {
            if (handle == null) {
                throw new IllegalArgumentException("MCP_CLIENT_HANDLE_REQUIRED");
            }
            callbacks = callbacks == null ? new ToolCallback[0] : callbacks.clone();
        }

        @Override
        public ToolCallback[] callbacks() {
            return callbacks.clone();
        }
    }
}
