package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP runtime boundary for client/session resolution, serialized remote calls,
 * protocol result access and connection invalidation.
 */
@Service
public final class OpsMcpRuntimeInvoker {

    private final OpsMcpRemoteClientAdapter remoteClients;
    private final OpsMcpRemoteInvocationAdapter remoteInvocations;

    public OpsMcpRuntimeInvoker(
            OpsMcpRemoteClientAdapter remoteClients,
            OpsMcpRemoteInvocationAdapter remoteInvocations) {
        if (remoteClients == null) {
            throw new IllegalArgumentException("MCP_REMOTE_CLIENT_ADAPTER_REQUIRED");
        }
        if (remoteInvocations == null) {
            throw new IllegalArgumentException("MCP_REMOTE_INVOCATION_ADAPTER_REQUIRED");
        }
        this.remoteClients = remoteClients;
        this.remoteInvocations = remoteInvocations;
    }

    public OpsMcpRemoteClientAdapter.Session open(OpsMcpServerConfig config) {
        return remoteClients.open(requireConfig(config));
    }

    public ToolCallback find(OpsMcpServerConfig config, String remoteToolName) {
        return remoteClients.find(requireConfig(config), remoteToolName);
    }

    public java.util.List<Map<String,Object>> currentLocalDefinitions(OpsMcpServerConfig config) {
        return remoteClients.currentLocalDefinitions(requireConfig(config));
    }

    public Map<String,Object> currentLocalDefinition(OpsMcpServerConfig config,String name) {
        return remoteClients.currentLocalDefinition(requireConfig(config),name);
    }

    public ToolCallback serialized(
            OpsMcpRemoteClientAdapter.Session session,
            ToolCallback callback) {
        return remoteInvocations.serialized(session, callback);
    }

    public String invoke(
            OpsMcpServerConfig config,
            String remoteToolName,
            String remoteArgs) {
        return remoteInvocations.invoke(requireConfig(config), remoteToolName, remoteArgs);
    }

    public Map<String, Object> inspectDefinition(
            OpsMcpServerConfig config,
            String remoteToolName) {
        return remoteClients.inspectDefinition(requireConfig(config), remoteToolName);
    }

    public List<Map<String, Object>> inspectDefinitions(OpsMcpServerConfig config) {
        return remoteClients.inspectDefinitions(requireConfig(config));
    }

    public String summarizeInput(String toolInput) {
        return remoteInvocations.summarizeInput(toolInput);
    }

    public void invalidateAll() {
        remoteClients.invalidateAll();
    }

    private OpsMcpServerConfig requireConfig(OpsMcpServerConfig config) {
        if (config == null) throw new IllegalArgumentException("MCP_SERVER_CONFIG_REQUIRED");
        return config;
    }
}
