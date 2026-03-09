package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdCommand;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdReceipt;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed guard around the first approved business write MCP Tool. */
@Component
public final class OpsUpdateAlertThresholdInvocationContract
        implements OpsToolInvocationContract {

    private final Clock clock;

    public OpsUpdateAlertThresholdInvocationContract() {
        this(Clock.systemUTC());
    }

    OpsUpdateAlertThresholdInvocationContract(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_CLOCK_REQUIRED");
        this.clock = clock;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return target != null && UpdateAlertThresholdPolicy.matches(
                target.toolsetId(), target.toolName());
    }

    @Override
    public ToolExecutionRequest prepare(
            ToolExecutionTarget target,
            ToolExecutionRequest request) {
        if (request == null) throw new IllegalArgumentException("TOOL_EXECUTION_REQUEST_REQUIRED");
        String executionKey = first(
                request.requestContext().get("idempotencyKey"),
                request.arguments().get("executionKey"));
        Instant deadline = deadline(first(
                request.requestContext().get("deadline"),
                request.arguments().get("deadline")));
        UpdateAlertThresholdCommand command = UpdateAlertThresholdCommand.from(
                request.arguments(),
                request.projectId(),
                executionKey,
                deadline,
                request.actor());
        command.validateDeadline(clock);

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("projectId", command.projectId());
        normalized.put("metric", command.metric());
        normalized.put("expectedValue", command.expectedValue());
        normalized.put("expectedVersion", command.expectedVersion());
        normalized.put("newValue", command.newValue());
        normalized.put("approvalId", command.approvalId());
        normalized.put("executionKey", command.executionKey());
        normalized.put("deadline", command.deadline().toString());
        normalized.put("actor", command.actor());
        return new ToolExecutionRequest(
                request.projectId(),
                request.userId(),
                request.actor(),
                request.toolsetId(),
                request.toolName(),
                request.scope(),
                normalized,
                request.sessionId(),
                request.runId(),
                request.requestContext(),
                request.landingContext());
    }

    @Override
    public Object validateOutput(
            ToolExecutionTarget target,
            ToolExecutionRequest preparedRequest,
            Object output) {
        if (!(output instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_RECEIPT_OBJECT_REQUIRED");
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        raw.forEach((key, value) -> typed.put(String.valueOf(key), value));
        UpdateAlertThresholdCommand command = UpdateAlertThresholdCommand.from(
                preparedRequest.arguments(),
                preparedRequest.projectId(),
                first(preparedRequest.requestContext().get("idempotencyKey"),
                        preparedRequest.arguments().get("executionKey")),
                deadline(first(preparedRequest.requestContext().get("deadline"),
                        preparedRequest.arguments().get("deadline"))),
                preparedRequest.actor());
        UpdateAlertThresholdReceipt receipt = UpdateAlertThresholdReceipt.from(typed);
        receipt.verify(command, target.toolName());
        Map<String, Object> normalized = new LinkedHashMap<>(typed);
        normalized.put("status", receipt.status());
        normalized.put("receiptId", receipt.receiptId());
        normalized.put("operation", receipt.operation());
        normalized.put("executionKey", receipt.executionKey());
        normalized.put("projectId", receipt.projectId());
        normalized.put("metric", receipt.metric());
        normalized.put("approvalId", receipt.approvalId());
        normalized.put("actor", receipt.actor());
        normalized.put("expectedVersion", receipt.expectedVersion());
        normalized.put("hashVersion", receipt.hashVersion());
        normalized.put("operationInputHash", receipt.operationInputHash());
        normalized.put("previousValue", receipt.previousValue());
        normalized.put("currentValue", receipt.currentValue());
        normalized.put("currentVersion", receipt.currentVersion());
        normalized.put("resultHash", receipt.resultHash());
        normalized.put("completedAt", receipt.completedAt().toString());
        return Map.copyOf(normalized);
    }

    private Instant deadline(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            return Instant.parse(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("UPDATE_ALERT_THRESHOLD_DEADLINE_INVALID", error);
        }
    }

    private String first(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            String normalized = value == null ? "" : String.valueOf(value).trim();
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }
}
