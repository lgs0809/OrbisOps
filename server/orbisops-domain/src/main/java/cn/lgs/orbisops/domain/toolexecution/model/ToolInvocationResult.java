package cn.lgs.orbisops.domain.toolexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Unified Tool outcome used by Agent, Workflow and approved side-effect callers. */
public record ToolInvocationResult(
        ToolInvocationDisposition disposition,
        String reasonCode,
        boolean executedProductionAction,
        String receiptId,
        String resultHash,
        Map<String, Object> output
) {

    public ToolInvocationResult {
        if (disposition == null) throw new IllegalArgumentException("TOOL_INVOCATION_DISPOSITION_REQUIRED");
        reasonCode = text(reasonCode);
        receiptId = text(receiptId);
        resultHash = text(resultHash);
        output = output == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(output));
        if ((disposition == ToolInvocationDisposition.BLOCKED
                || disposition == ToolInvocationDisposition.UNKNOWN)
                && executedProductionAction) {
            throw new IllegalArgumentException("TOOL_INVOCATION_EXECUTION_DISPOSITION_CONFLICT");
        }
    }

    public static ToolInvocationResult from(
            ToolExecutionResult result,
            ToolExecutionScope scope) {
        if (result == null) throw new IllegalArgumentException("TOOL_EXECUTION_RESULT_REQUIRED");
        if (scope == null) throw new IllegalArgumentException("TOOL_EXECUTION_SCOPE_REQUIRED");
        ToolInvocationDisposition disposition = result.success()
                ? ToolInvocationDisposition.SUCCEEDED
                : result.allowed()
                        ? ToolInvocationDisposition.FAILED
                        : ToolInvocationDisposition.BLOCKED;
        boolean productionAction = disposition == ToolInvocationDisposition.SUCCEEDED
                && scope == ToolExecutionScope.APPROVED_LANDING
                && result.tool().semantics().writesTargetResource();
        return new ToolInvocationResult(
                disposition,
                disposition == ToolInvocationDisposition.SUCCEEDED
                        ? ""
                        : first(result.errorCode(), result.status()),
                productionAction,
                first(result.payload().get("receiptId"), result.payload().get("providerReceiptId")),
                first(
                        result.payload().get("resultHash"),
                        result.payload().get("providerResultHash"),
                        result.recorded().outputHash()),
                result.payload());
    }

    public static ToolInvocationResult failed(String reasonCode, Map<String, Object> output) {
        return new ToolInvocationResult(
                ToolInvocationDisposition.FAILED,
                reasonCode,
                false,
                "",
                "",
                output);
    }

    public static ToolInvocationResult unknown(String reasonCode, Map<String, Object> output) {
        return new ToolInvocationResult(
                ToolInvocationDisposition.UNKNOWN,
                reasonCode,
                false,
                "",
                "",
                output);
    }

    private static String first(Object... values) {
        for (Object value : values) {
            String normalized = value == null ? "" : String.valueOf(value).trim();
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
