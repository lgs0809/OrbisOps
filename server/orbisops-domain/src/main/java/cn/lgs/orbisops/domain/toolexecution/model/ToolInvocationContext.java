package cn.lgs.orbisops.domain.toolexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable invocation context shared by Local, MCP and Built-in providers. */
public record ToolInvocationContext(
        String projectId,
        String userId,
        String actor,
        String sessionId,
        String runId,
        ToolExecutionScope scope,
        Map<String, Object> attributes,
        Map<String, Object> landingAuthority
) {

    public ToolInvocationContext {
        projectId = text(projectId);
        userId = text(userId);
        actor = text(actor);
        sessionId = text(sessionId);
        runId = text(runId);
        scope = scope == null ? ToolExecutionScope.PRE_APPROVAL_WORKFLOW : scope;
        attributes = immutable(attributes);
        landingAuthority = immutable(landingAuthority);
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        return source == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
