package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;

public interface McpExecutionRecordPort {

    McpExecutionRecordedResult record(McpExecutionRecordCommand command);

    record McpExecutionRecordCommand(
            McpExecutionRequest request,
            String toolName,
            String source,
            String status,
            Object output,
            long durationMs,
            boolean verified) {

        public McpExecutionRecordCommand {
            if (request == null) throw new IllegalArgumentException("MCP_EXECUTION_REQUEST_REQUIRED");
            toolName = required(toolName, "MCP_EXECUTION_TOOL_REQUIRED");
            source = required(source, "MCP_EXECUTION_SOURCE_REQUIRED");
            status = required(status, "MCP_EXECUTION_STATUS_REQUIRED").toUpperCase();
            durationMs = Math.max(0L, durationMs);
        }

        private static String required(String value, String error) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(error);
            return normalized;
        }
    }
}
