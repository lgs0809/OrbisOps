package cn.lgs.orbisops.domain.toolexecution.model;

import cn.lgs.orbisops.domain.toolset.model.ToolReference;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolExecutionRequest(
        String projectId,
        String userId,
        String actor,
        String toolsetId,
        String toolName,
        ToolExecutionScope scope,
        Map<String, Object> arguments,
        String sessionId,
        String runId,
        Map<String, Object> requestContext,
        Map<String, Object> landingContext) {

    private static final String TIMEOUT_MS = "toolTimeoutMs";

    public ToolExecutionRequest {
        projectId = value(projectId);
        userId = value(userId);
        actor = value(actor);
        toolsetId = required(toolsetId, "TOOL_EXECUTION_TOOLSET_REQUIRED");
        toolName = required(toolName, "TOOL_EXECUTION_TOOL_REQUIRED");
        scope = scope == null ? ToolExecutionScope.PRE_APPROVAL_WORKFLOW : scope;
        arguments = immutable(arguments);
        sessionId = value(sessionId);
        runId = value(runId);
        requestContext = immutable(requestContext);
        landingContext = immutable(landingContext);
    }

    public ToolInvocation invocation() {
        return new ToolInvocation(
                new ToolReference(toolsetId, toolName),
                arguments,
                new ToolInvocationContext(
                        projectId,
                        userId,
                        actor,
                        sessionId,
                        runId,
                        scope,
                        requestContext,
                        landingContext),
                timeout(requestContext.get(TIMEOUT_MS)));
    }

    public static ToolExecutionRequest from(ToolInvocation invocation) {
        if (invocation == null) throw new IllegalArgumentException("TOOL_INVOCATION_REQUIRED");
        ToolInvocationContext context = invocation.context();
        Map<String, Object> attributes = new LinkedHashMap<>(context.attributes());
        attributes.put(TIMEOUT_MS, invocation.timeout().toMillis());
        return new ToolExecutionRequest(
                context.projectId(),
                context.userId(),
                context.actor(),
                invocation.reference().toolsetId(),
                invocation.reference().toolName(),
                context.scope(),
                invocation.arguments(),
                context.sessionId(),
                context.runId(),
                attributes,
                context.landingAuthority());
    }

    private static Duration timeout(Object value) {
        if (value == null) return null;
        try {
            long milliseconds = Long.parseLong(String.valueOf(value));
            return Duration.ofMillis(milliseconds);
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("TOOL_INVOCATION_TIMEOUT_INVALID");
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
