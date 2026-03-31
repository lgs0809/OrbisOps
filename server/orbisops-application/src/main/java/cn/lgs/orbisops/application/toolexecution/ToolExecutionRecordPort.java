package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRecordedResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;

public interface ToolExecutionRecordPort {

    ToolExecutionRecordedResult record(ToolExecutionRecordCommand command);

    record ToolExecutionRecordCommand(
            ToolExecutionRequest request,
            ToolExecutionTarget target,
            String source,
            String evidenceSourceType,
            String outputStatus,
            Object output,
            long durationMs,
            boolean verifiedEvidence) {

        public ToolExecutionRecordCommand {
            if (request == null) throw new IllegalArgumentException("TOOL_EXECUTION_REQUEST_REQUIRED");
            if (target == null) throw new IllegalArgumentException("TOOL_EXECUTION_TARGET_REQUIRED");
            source = required(source, "TOOL_EXECUTION_SOURCE_REQUIRED");
            evidenceSourceType = required(evidenceSourceType, "TOOL_EXECUTION_EVIDENCE_SOURCE_REQUIRED");
            outputStatus = required(outputStatus, "TOOL_EXECUTION_OUTPUT_STATUS_REQUIRED");
            durationMs = Math.max(0L, durationMs);
        }

        private static String required(String value, String error) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(error);
            return normalized;
        }
    }
}
