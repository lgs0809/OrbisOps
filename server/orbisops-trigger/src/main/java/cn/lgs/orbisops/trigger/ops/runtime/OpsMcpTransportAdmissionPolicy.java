package cn.lgs.orbisops.trigger.ops.runtime;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure admission rules for MCP transport, command, arguments, address, and timeout. */
final class OpsMcpTransportAdmissionPolicy {

    private static final Pattern UNSAFE_ARG_PATTERN = Pattern.compile("[\\r\\n\\u0000]");

    String normalizeTransport(String transport) {
        return OpsMcpTransportSecuritySettings.normalizeTransport(transport);
    }

    int normalizeTimeout(Integer timeoutSeconds, OpsMcpTransportSecuritySettings settings) {
        OpsMcpTransportSecuritySettings effective = effective(settings);
        int timeout = timeoutSeconds == null ? 30 : Math.max(1, timeoutSeconds);
        return Math.min(timeout, effective.maxTimeoutSeconds());
    }

    void assertTransportAllowed(
            OpsMcpServerConfig config,
            String transport,
            OpsMcpTransportSecuritySettings settings) {
        if (platformGenerated(config) && "stdio".equals(normalizeTransport(transport))) {
            return;
        }
        assertTransportAllowed(serverName(config), transport, settings);
    }

    void assertTransportAllowed(
            String serverName,
            String transport,
            OpsMcpTransportSecuritySettings settings) {
        OpsMcpTransportSecuritySettings effective = effective(settings);
        if (!effective.enabled()) {
            return;
        }
        if (!effective.allowedTransports().contains(transport)) {
            throw new IllegalArgumentException(
                    "MCP transport 未在白名单中，server=" + safe(serverName)
                            + ", transport=" + transport);
        }
    }

    void assertStdioAllowed(
            OpsMcpServerConfig config,
            String commandValue,
            OpsMcpTransportSecuritySettings settings) {
        if (platformGenerated(config) && "node".equals(normalize(commandName(commandValue)))) {
            return;
        }
        assertStdioAllowed(serverName(config), commandValue, settings);
    }

    void assertStdioAllowed(
            String serverName,
            String commandValue,
            OpsMcpTransportSecuritySettings settings) {
        OpsMcpTransportSecuritySettings effective = effective(settings);
        if (!effective.enabled()) {
            return;
        }
        String command = commandName(commandValue);
        if (!effective.allowedStdioCommands().contains(
                OpsMcpTransportSecuritySettings.normalize(command))) {
            throw new IllegalArgumentException(
                    "stdio MCP command 未在白名单中，server=" + safe(serverName)
                            + ", command=" + command);
        }
    }

    void assertArgsAllowed(
            String serverName,
            List<String> args,
            OpsMcpTransportSecuritySettings settings) {
        OpsMcpTransportSecuritySettings effective = effective(settings);
        if (!effective.enabled() || args == null) {
            return;
        }
        for (String arg : args) {
            if (arg != null && (arg.length() > 512 || UNSAFE_ARG_PATTERN.matcher(arg).find())) {
                throw new IllegalArgumentException(
                        "stdio MCP args 包含非法字符或过长，server=" + safe(serverName));
            }
        }
    }

    void assertRemoteAddressAllowed(
            String serverName,
            String baseUri,
            OpsMcpTransportSecuritySettings settings) {
        OpsMcpTransportSecuritySettings effective = effective(settings);
        if (!effective.enabled()) {
            return;
        }
        URI uri = URI.create(baseUri);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "sse MCP 仅允许 http/https，server=" + safe(serverName));
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new IllegalArgumentException(
                    "sse MCP URL 不允许携带 userInfo，server=" + safe(serverName));
        }
        String host = OpsMcpTransportSecuritySettings.normalize(uri.getHost());
        if (!effective.allowedRemoteHosts().contains("*")
                && !effective.allowedRemoteHosts().contains(host)) {
            throw new IllegalArgumentException(
                    "sse MCP host 未在白名单中，server=" + safe(serverName)
                            + ", host=" + uri.getHost());
        }
    }

    private String commandName(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String trimmed = command.trim();
        try {
            return Path.of(trimmed).getFileName().toString();
        } catch (RuntimeException ignored) {
            return trimmed;
        }
    }

    private boolean platformGenerated(OpsMcpServerConfig config) {
        Map<String, String> capabilities = config == null ? null : config.getToolCapabilities();
        return capabilities != null
                && "true".equalsIgnoreCase(String.valueOf(capabilities.get("platformGenerated")));
    }

    private String serverName(OpsMcpServerConfig config) {
        return config == null || config.getName() == null ? "" : config.getName();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private OpsMcpTransportSecuritySettings effective(OpsMcpTransportSecuritySettings settings) {
        return settings == null ? OpsMcpTransportSecuritySettings.defaults() : settings;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
