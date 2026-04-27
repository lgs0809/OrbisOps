package cn.lgs.orbisops.domain.channel.service;

import java.util.regex.Pattern;

/**
 * Domain policy for preventing credentials from crossing an outbound channel boundary.
 */
public final class ChannelOutboundContentPolicy {

    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)(\\b[A-Za-z0-9_]*(secret|token|password|credential|private[_-]?key|api[_-]?key|authorization|cookie|access[_-]?key)[A-Za-z0-9_]*\\s*[:=]\\s*)([^\\s,;&\"}]+)");
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*");

    public String sanitize(String value) {
        if (value == null || value.isBlank()) return "";
        String masked = KEY_VALUE_SECRET.matcher(value).replaceAll("$1***");
        return BEARER_TOKEN.matcher(masked).replaceAll("$1***");
    }
}
