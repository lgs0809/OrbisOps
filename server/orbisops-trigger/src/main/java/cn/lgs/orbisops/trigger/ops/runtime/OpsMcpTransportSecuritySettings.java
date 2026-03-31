package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Typed allowlists and limits for MCP transport construction. */
public record OpsMcpTransportSecuritySettings(
        boolean enabled,
        Set<String> allowedTransports,
        Set<String> allowedStdioCommands,
        Set<String> allowedRemoteHosts,
        Set<String> allowedEnvKeys,
        Set<String> allowedHeaderKeys,
        int maxTimeoutSeconds) {

    public static final String DEFAULT_TRANSPORTS = "stdio,sse,streamable-http";
    public static final String DEFAULT_STDIO_COMMANDS = "npx,node,python,python3,uvx,java";
    public static final String DEFAULT_REMOTE_HOSTS = "127.0.0.1,localhost";
    // Execution metadata is not authorization; reviewed tool policy and Landing authority still apply.
    public static final String DEFAULT_HEADER_KEYS =
            "Authorization,X-API-Key,X-Ops-Execution-Key,X-Ops-Fencing-Token,X-Ops-Deadline";

    public OpsMcpTransportSecuritySettings {
        allowedTransports = immutableSet(allowedTransports, true);
        allowedStdioCommands = immutableSet(allowedStdioCommands, false);
        allowedRemoteHosts = immutableSet(allowedRemoteHosts, false);
        allowedEnvKeys = immutableSet(allowedEnvKeys, false);
        allowedHeaderKeys = immutableSet(allowedHeaderKeys, false);
        maxTimeoutSeconds = maxTimeoutSeconds < 1 || maxTimeoutSeconds > 3_600
                ? 60
                : maxTimeoutSeconds;
    }

    public static OpsMcpTransportSecuritySettings fromRaw(
            boolean enabled,
            String allowedTransports,
            String allowedStdioCommands,
            String allowedRemoteHosts,
            String allowedEnvKeys,
            String allowedHeaderKeys,
            int maxTimeoutSeconds) {
        return new OpsMcpTransportSecuritySettings(
                enabled,
                csvSet(allowedTransports, true),
                csvSet(allowedStdioCommands, false),
                csvSet(allowedRemoteHosts, false),
                csvSet(allowedEnvKeys, false),
                csvSet(allowedHeaderKeys, false),
                maxTimeoutSeconds);
    }

    public static OpsMcpTransportSecuritySettings defaults() {
        return fromRaw(
                true,
                DEFAULT_TRANSPORTS,
                DEFAULT_STDIO_COMMANDS,
                DEFAULT_REMOTE_HOSTS,
                "",
                DEFAULT_HEADER_KEYS,
                60);
    }

    static OpsMcpTransportSecuritySettings legacyConstructorDefaults() {
        OpsMcpTransportSecuritySettings defaults = defaults();
        return new OpsMcpTransportSecuritySettings(
                false,
                defaults.allowedTransports(),
                defaults.allowedStdioCommands(),
                defaults.allowedRemoteHosts(),
                defaults.allowedEnvKeys(),
                defaults.allowedHeaderKeys(),
                1);
    }

    static String normalizeTransport(String value) {
        if (value == null || value.isBlank()) {
            return "stdio";
        }
        String normalized = normalize(value).replace('_', '-');
        if ("streamablehttp".equals(normalized)
                || "streamable-http".equals(normalized)
                || "http".equals(normalized)) {
            return "streamable-http";
        }
        return normalized;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> csvSet(String value, boolean transports) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String normalized = transports ? normalizeTransport(item) : normalize(item);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static Set<String> immutableSet(Set<String> source, boolean transports) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : source) {
            String normalized = transports ? normalizeTransport(item) : normalize(item);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
