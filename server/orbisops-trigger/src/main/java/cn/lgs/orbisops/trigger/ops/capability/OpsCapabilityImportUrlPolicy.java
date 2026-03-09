package cn.lgs.orbisops.trigger.ops.capability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** URL and DNS policy for externally supplied Skill and MCP endpoints. */
@Component
public class OpsCapabilityImportUrlPolicy {

    private final OpsCapabilityImportSettings settings;

    public OpsCapabilityImportUrlPolicy() {
        this(OpsCapabilityImportSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsCapabilityImportUrlPolicy(OpsCapabilityImportSettings settings) {
        this.settings = settings == null
                ? OpsCapabilityImportSettings.defaults()
                : settings;
    }

    public URI validate(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_URL_REQUIRED");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_URL_INVALID", error);
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_URL_INVALID");
        }
        if (containsSecretQuery(uri)) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_SECRET_IN_URL_FORBIDDEN");
        }
        boolean loopback = explicitLoopback(host);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !(settings.allowLoopback()
                && loopback
                && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_HTTPS_REQUIRED");
        }
        if (!settings.hostAllowed(host)) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_HOST_NOT_ALLOWED");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException error) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_DNS_UNRESOLVED", error);
        }
        if (addresses.length == 0) {
            throw new IllegalArgumentException("CAPABILITY_IMPORT_DNS_UNRESOLVED");
        }
        for (InetAddress address : addresses) {
            if (blocked(address)
                    && !(settings.allowLoopback()
                    && loopback
                    && address.isLoopbackAddress())) {
                throw new IllegalArgumentException("CAPABILITY_IMPORT_PRIVATE_ADDRESS_BLOCKED");
            }
        }
        return uri;
    }

    private boolean containsSecretQuery(URI uri) {
        String query = uri.getRawQuery();
        return StringUtils.hasText(query)
                && query.toLowerCase(Locale.ROOT)
                .matches(".*(?:token|secret|password|passwd|api[_-]?key|access[_-]?key)=.*");
    }

    private boolean explicitLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private boolean blocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = Byte.toUnsignedInt(bytes[0]);
            int b = Byte.toUnsignedInt(bytes[1]);
            return a == 0
                    || a == 10
                    || a == 127
                    || (a == 169 && b == 254)
                    || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168)
                    || (a == 100 && b >= 64 && b <= 127)
                    || a >= 224;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xFE) == 0xFC
                    || (first == 0xFE
                    && (Byte.toUnsignedInt(bytes[1]) & 0xC0) == 0x80);
        }
        return true;
    }
}
