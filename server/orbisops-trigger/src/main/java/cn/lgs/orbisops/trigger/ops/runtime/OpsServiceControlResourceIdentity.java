package cn.lgs.orbisops.trigger.ops.runtime;

import java.net.URI;

import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.text;

/** Single authoritative projection from a service-control resource endpoint to its service id. */
public final class OpsServiceControlResourceIdentity {

    private OpsServiceControlResourceIdentity() {
    }

    public static String serviceName(String endpoint) {
        String value = text(endpoint, "");
        try {
            URI uri = URI.create(value);
            String host = text(uri.getHost(), "");
            if (!host.isBlank()) return host;
            String authority = text(uri.getAuthority(), "");
            if (!authority.isBlank()) return authority;
        } catch (RuntimeException ignored) {
            // Fall through to strict textual extraction below.
        }
        String normalized = value.replaceFirst("^[A-Za-z][A-Za-z0-9+.-]*://", "");
        int slash = normalized.indexOf('/');
        if (slash >= 0) normalized = normalized.substring(0, slash);
        normalized = normalized.trim();
        if (normalized.isBlank() || !normalized.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
            throw new IllegalArgumentException("SERVICE_CONTROL_ENDPOINT_INVALID");
        }
        return normalized;
    }
}
