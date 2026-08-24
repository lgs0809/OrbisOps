package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;

public interface McpExecutionRecordPort {

    McpExecutionRecordedResult record(McpExecutionRecordCommand command);

    /** Assigned only by the application control flow, never inferred from a provider response. */
    enum DispatchDisposition { NOT_PROVEN, PRE_DISPATCH_DENIED }

    record McpExecutionRecordCommand(
            McpExecutionRequest request,
            String toolName,
            String source,
            String status,
            Object output,
            long durationMs,
            boolean verified,
            DispatchDisposition dispatchDisposition) {

        public McpExecutionRecordCommand(McpExecutionRequest request, String toolName, String source,
                String status, Object output, long durationMs, boolean verified) {
            this(request, toolName, source, status, output, durationMs, verified, DispatchDisposition.NOT_PROVEN);
        }

        public McpExecutionRecordCommand {
            if (request == null) throw new IllegalArgumentException("MCP_EXECUTION_REQUEST_REQUIRED");
            toolName = required(toolName, "MCP_EXECUTION_TOOL_REQUIRED");
            source = required(source, "MCP_EXECUTION_SOURCE_REQUIRED");
            status = required(status, "MCP_EXECUTION_STATUS_REQUIRED").toUpperCase();
            durationMs = Math.max(0L, durationMs);
            dispatchDisposition = dispatchDisposition == null ? DispatchDisposition.NOT_PROVEN : dispatchDisposition;
            if (dispatchDisposition == DispatchDisposition.PRE_DISPATCH_DENIED
                    && (!"TOOL_BLOCKED".equals(source) || !"BLOCKED".equals(status) || verified))
                throw new IllegalArgumentException("MCP_PRE_DISPATCH_DENIAL_INCONSISTENT");
        }

        private static String required(String value, String error) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(error);
            return normalized;
        }
    }
}
