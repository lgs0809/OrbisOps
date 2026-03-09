package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure env/header allowlist and sensitive-key filtering for MCP transports. */
final class OpsMcpTransportSecretFilter {

    private static final Pattern SENSITIVE_KEY_SEGMENT_PATTERN =
            Pattern.compile("(?i)(^|[_.-])(secret|token|password|credential|private|key)([_.-]|$)");
    private static final Pattern COMPACT_SENSITIVE_KEY_PATTERN =
            Pattern.compile("(?i).*(apikey|accesskey|secretkey|privatekey).*");

    Map<String, String> safeEnv(
            String serverName,
            Map<String, String> source,
            OpsMcpTransportSecuritySettings settings) {
        OpsMcpTransportSecuritySettings effective = effective(settings);
        if (!effective.enabled() || source == null || source.isEmpty()) {
            return source == null ? Map.of() : source;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalized = OpsMcpTransportSecuritySettings.normalize(key);
            boolean sensitive = sensitiveKey(key);
            if (sensitive && !effective.allowedEnvKeys().contains(normalized)) {
                throw new IllegalArgumentException(
                        "MCP env 包含敏感键但未显式放行，server=" + safe(serverName)
                                + ", key=" + key);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    Map<String, String> safeHeaders(
            String serverName,
            Map<String, String> source,
            OpsMcpTransportSecuritySettings settings) {
        OpsMcpTransportSecuritySettings effective = effective(settings);
        if (!effective.enabled() || source == null || source.isEmpty()) {
            return source == null ? Map.of() : source;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (!effective.allowedHeaderKeys().contains(
                    OpsMcpTransportSecuritySettings.normalize(key))) {
                throw new IllegalArgumentException(
                        "MCP header 未在白名单中，server=" + safe(serverName)
                                + ", header=" + key);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private boolean sensitiveKey(String key) {
        if (key == null || key.isBlank()) return false;
        return SENSITIVE_KEY_SEGMENT_PATTERN.matcher(key).find()
                || COMPACT_SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }

    private OpsMcpTransportSecuritySettings effective(OpsMcpTransportSecuritySettings settings) {
        return settings == null ? OpsMcpTransportSecuritySettings.defaults() : settings;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
