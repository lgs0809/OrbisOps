package cn.lgs.orbisops.trigger.ops.channel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Typed SSRF settings for outbound channel webhooks. */
public record OpsOutboundUrlSettings(
        boolean allowLoopback,
        Set<String> allowedHosts) {

    public OpsOutboundUrlSettings {
        allowedHosts = immutableHosts(allowedHosts);
    }

    public static OpsOutboundUrlSettings fromRaw(
            boolean allowLoopback,
            String allowedHosts) {
        Set<String> hosts = new LinkedHashSet<>();
        if (allowedHosts != null && !allowedHosts.isBlank()) {
            for (String value : allowedHosts.split(",")) {
                String normalized = normalize(value);
                if (!normalized.isEmpty()) {
                    hosts.add(normalized);
                }
            }
        }
        return new OpsOutboundUrlSettings(allowLoopback, hosts);
    }

    public static OpsOutboundUrlSettings defaults() {
        return new OpsOutboundUrlSettings(false, Set.of());
    }

    private static Set<String> immutableHosts(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
