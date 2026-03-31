package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.application.mcpexecution.McpExecutionRemotePort;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsMcpExecutionRemoteAdapter implements McpExecutionRemotePort {

    private final ObjectProvider<OpsMcpToolProvider> providers;
    private final OpsMcpExecutionMapper mapper;

    public OpsMcpExecutionRemoteAdapter(
            ObjectProvider<OpsMcpToolProvider> providers,
            OpsMcpExecutionMapper mapper) {
        this.providers = providers;
        this.mapper = mapper;
    }

    @Override
    public Map<String, Object> inspect(McpExecutionConfig config, String toolName) {
        return provider().inspectRemoteToolDefinition(mapper.legacy(config, "PREPARE"), toolName);
    }

    @Override
    public String call(McpExecutionConfig config, String rawInput, String stage) {
        return provider().callProgressiveDirect(mapper.legacy(config, stage), rawInput);
    }

    private OpsMcpToolProvider provider() {
        OpsMcpToolProvider provider = providers == null ? null : providers.getIfAvailable();
        if (provider == null) throw new IllegalStateException("MCP_REMOTE_PROVIDER_UNAVAILABLE");
        return provider;
    }
}
