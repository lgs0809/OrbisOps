package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;

public final class McpExecutionDeniedException extends SecurityException {

    private final McpExecutionResponse response;

    public McpExecutionDeniedException(String message, McpExecutionResponse response) {
        super(message);
        if (response == null) throw new IllegalArgumentException("MCP_EXECUTION_DENIED_RESPONSE_REQUIRED");
        this.response = response;
    }

    public McpExecutionResponse response() {
        return response;
    }
}
