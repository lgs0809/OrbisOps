package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;

import java.util.Map;

public interface McpExecutionRemotePort {

    Map<String, Object> inspect(McpExecutionConfig config, String toolName);

    String call(McpExecutionConfig config, String rawInput, String stage);
}
