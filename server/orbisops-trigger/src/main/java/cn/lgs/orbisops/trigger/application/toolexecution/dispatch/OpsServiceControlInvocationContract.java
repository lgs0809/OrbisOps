package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Platform-trusted identity, idempotency and receipt contract for service-control MCP tools. */
@Component
public final class OpsServiceControlInvocationContract implements OpsToolInvocationContract {

    private static final Set<String> SUPPORTED = Set.of(
            "restart_service_dry_run",
            "restart_service");

    @Override
    public boolean supports(ToolExecutionTarget target) {
        if (target == null) return false;
        String remote = target.providerDescriptor() == null
                ? ""
                : text(target.providerDescriptor().remoteToolName());
        return SUPPORTED.contains(first(remote, target.toolName()));
    }

    @Override
    public ToolExecutionRequest prepare(ToolExecutionTarget target,
                                        ToolExecutionRequest request) {
        if (request == null) throw new IllegalArgumentException("TOOL_EXECUTION_REQUEST_REQUIRED");
        String toolName = target == null || target.providerDescriptor() == null
                ? text(target == null ? "" : target.toolName())
                : first(target.providerDescriptor().remoteToolName(), target.toolName());
        Map<String, Object> arguments = new LinkedHashMap<>(request.arguments());
        String service = required(arguments.get("service"), "SERVICE_CONTROL_SERVICE_REQUIRED");
        arguments.clear();
        arguments.put("projectId", required(request.projectId(), "SERVICE_CONTROL_PROJECT_REQUIRED"));
        arguments.put("service", service);
        arguments.put("actor", required(request.actor(), "SERVICE_CONTROL_ACTOR_REQUIRED"));
        Object expectedVersion = request.arguments().get("expectedVersion");
        if (expectedVersion != null) {
            arguments.put("expectedVersion", integer(expectedVersion, "SERVICE_CONTROL_EXPECTED_VERSION_INVALID"));
        }

        if ("restart_service".equals(toolName)) {
            if (expectedVersion == null) {
                throw new IllegalArgumentException("SERVICE_CONTROL_EXPECTED_VERSION_REQUIRED");
            }
            String executionKey = required(first(
                    request.requestContext().get("idempotencyKey"),
                    request.landingContext().get("executionKey"),
                    request.arguments().get("executionKey")),
                    "SERVICE_CONTROL_EXECUTION_KEY_REQUIRED");
            String deadline = required(first(
                    request.requestContext().get("deadline"),
                    request.requestContext().get("authorityDeadline"),
                    request.landingContext().get("deadline"),
                    request.arguments().get("deadline")),
                    "SERVICE_CONTROL_DEADLINE_REQUIRED");
            try {
                Instant.parse(deadline);
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("SERVICE_CONTROL_DEADLINE_INVALID", error);
            }
            arguments.put("executionKey", executionKey);
            arguments.put("deadline", deadline);
        }
        return new ToolExecutionRequest(
                request.projectId(),
                request.userId(),
                request.actor(),
                request.toolsetId(),
                request.toolName(),
                request.scope(),
                arguments,
                request.sessionId(),
                request.runId(),
                request.requestContext(),
                request.landingContext());
    }

    @Override
    public Object validateOutput(ToolExecutionTarget target,
                                 ToolExecutionRequest preparedRequest,
                                 Object output) {
        if (!(output instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("SERVICE_CONTROL_RESULT_OBJECT_REQUIRED");
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        raw.forEach((key, value) -> envelope.put(String.valueOf(key), value));
        Map<String, Object> result = providerResult(envelope);
        String status = text(result.get("status")).toUpperCase();
        if (!Set.of("SUCCEEDED", "PASSED").contains(status)) {
            throw new IllegalStateException("SERVICE_CONTROL_RESULT_NOT_SUCCESSFUL:" + status);
        }
        assertEqual("projectId", preparedRequest.arguments().get("projectId"), result.get("projectId"));
        assertEqual("service", preparedRequest.arguments().get("service"), result.get("service"));
        String remote = target == null || target.providerDescriptor() == null
                ? text(target == null ? "" : target.toolName())
                : first(target.providerDescriptor().remoteToolName(), target.toolName());
        if ("restart_service".equals(remote)) {
            assertEqual("executionKey", preparedRequest.arguments().get("executionKey"), result.get("executionKey"));
            assertEqual("actor", preparedRequest.arguments().get("actor"), result.get("actor"));
            required(result.get("receiptId"), "SERVICE_CONTROL_RECEIPT_ID_REQUIRED");
            String resultHash = required(result.get("resultHash"), "SERVICE_CONTROL_RESULT_HASH_REQUIRED");
            if (!resultHash.matches("sha256:[0-9a-f]{64}")) {
                throw new IllegalArgumentException("SERVICE_CONTROL_RESULT_HASH_INVALID");
            }
            integer(result.get("currentVersion"), "SERVICE_CONTROL_CURRENT_VERSION_INVALID");
            integer(result.get("currentRestartCount"), "SERVICE_CONTROL_RESTART_COUNT_INVALID");
        } else {
            Object writesTarget = result.get("writesTargetResource");
            if (!Boolean.FALSE.equals(writesTarget)) {
                throw new IllegalStateException("SERVICE_CONTROL_DRY_RUN_TARGET_WRITE_FORBIDDEN");
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, Object> providerResult(Map<String, Object> envelope) {
        Object nested = envelope.get("providerResult");
        if (nested instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return envelope;
    }

    private void assertEqual(String field, Object expected, Object actual) {
        if (!text(expected).equals(text(actual))) {
            throw new SecurityException("SERVICE_CONTROL_RESULT_IDENTITY_MISMATCH:" + field);
        }
    }

    private int integer(Object value, String error) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException(error);
        }
    }

    private String required(Object value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String first(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
