package cn.lgs.orbisops.domain.mcpexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpExecutionConfig(
        String name,
        String description,
        String projectId,
        String runId,
        String agentId,
        String nodeId,
        String mcpId,
        String toolId,
        String operationId,
        String transport,
        String command,
        String url,
        int timeoutSeconds,
        List<String> args,
        Map<String, String> env,
        Map<String, String> headers,
        Map<String, String> toolCapabilities,
        List<String> allowedTools,
        List<String> notificationTools,
        List<String> blockedTools,
        boolean landingApproved,
        String changePackageId,
        String approvedPackageHash,
        int approvedPackageVersion,
        java.time.Instant authorityDeadline) {

    public McpExecutionConfig(
        String name,
        String description,
        String projectId,
        String runId,
        String agentId,
        String nodeId,
        String mcpId,
        String toolId,
        String operationId,
        String transport,
        String command,
        String url,
        int timeoutSeconds,
        List<String> args,
        Map<String, String> env,
        Map<String, String> headers,
        Map<String, String> toolCapabilities,
        List<String> allowedTools,
        List<String> notificationTools,
        List<String> blockedTools,
        boolean landingApproved,
        String changePackageId,
        String approvedPackageHash,
        int approvedPackageVersion) {
        this(name, description, projectId, runId, agentId, nodeId, mcpId, toolId, operationId, transport, command, url, timeoutSeconds, args, env, headers, toolCapabilities, allowedTools, notificationTools, blockedTools, landingApproved, changePackageId, approvedPackageHash, approvedPackageVersion, null);
    }

    public McpExecutionConfig {
        name = value(name);
        description = value(description);
        projectId = required(projectId, "MCP_EXECUTION_PROJECT_ID_REQUIRED");
        runId = value(runId);
        agentId = value(agentId);
        nodeId = value(nodeId);
        mcpId = required(first(mcpId, name), "MCP_EXECUTION_MCP_ID_REQUIRED");
        toolId = first(toolId, mcpId);
        operationId = value(operationId);
        transport = value(transport);
        command = value(command);
        url = value(url);
        timeoutSeconds = Math.max(0, timeoutSeconds);
        args = immutable(args);
        env = immutable(env);
        headers = immutable(headers);
        toolCapabilities = immutable(toolCapabilities);
        allowedTools = immutable(allowedTools);
        notificationTools = immutable(notificationTools);
        blockedTools = immutable(blockedTools);
        changePackageId = value(changePackageId);
        approvedPackageHash = value(approvedPackageHash);
        approvedPackageVersion = Math.max(0, approvedPackageVersion);
    }

    public String toolsetId() {
        return "mcp." + mcpId;
    }

    private static String first(String primary, String fallback) {
        String value = value(primary);
        return value.isBlank() ? value(fallback) : value;
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return source == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
