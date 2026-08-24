package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Projects server-held execution identity only onto fields in the actual remote contract. */
final class OpsMcpRuntimeOwnedArguments {
    private OpsMcpRuntimeOwnedArguments() { }

    static Map<String, Object> project(OpsMcpServerConfig config, Map<String, Object> schema,
                                      Map<String, Object> arguments) {
        if (!owns(config)) return arguments;
        Object properties = schema.get("properties");
        if (!(properties instanceof Map<?, ?> declared)) return arguments;
        var projected = new LinkedHashMap<>(arguments);
        put(projected, declared, "projectId", config.getProjectId());
        put(projected, declared, "executionKey", config.getHeaders().get("X-Ops-Execution-Key"));
        put(projected, declared, "deadline", config.getAuthorityDeadline().toString());
        return Map.copyOf(projected);
    }

    private static boolean owns(OpsMcpServerConfig config) {
        return config != null && "LANDING".equals(config.getToolCallStage())
                && Boolean.TRUE.equals(config.getLandingApproved())
                && OpsToolsetRouter.LANDING_INTERNAL_CALLER.equals(config.getInternalCaller())
                && OpsToolsetRouter.LANDING_RUNTIME_TOKEN.equals(config.getLandingRuntimeToken())
                && text(config.getChangePackageId()) && text(config.getApprovedPackageHash())
                && config.getApprovedPackageVersion() != null && config.getApprovedPackageVersion() > 0
                && config.getAuthorityDeadline() != null
                && config.getHeaders() != null && text(config.getHeaders().get("X-Ops-Execution-Key"));
    }

    private static void put(Map<String, Object> projected, Map<?, ?> declared, String field, String value) {
        if (!declared.containsKey(field) || !text(value)) return;
        if (projected.containsKey(field) && !Objects.equals(projected.get(field), value)) {
            throw new SecurityException("MCP_RUNTIME_OWNED_ARGUMENT_MISMATCH:" + field);
        }
        projected.put(field, value);
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
}
