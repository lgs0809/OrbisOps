package cn.lgs.orbisops.trigger.ops.channel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** DNS-aware SSRF facade applied both when saving and dispatching a webhook. */
@Component
public class OpsOutboundUrlPolicy {

    private final OpsOutboundUrlSettings settings;
    private final OpsOutboundAddressPolicy addressPolicy;
    private final OpsOutboundDnsResolver dnsResolver;

    public OpsOutboundUrlPolicy() {
        this(
                OpsOutboundUrlSettings.defaults(),
                new OpsOutboundAddressPolicy(),
                new JvmOpsOutboundDnsResolver());
    }

    @Autowired
    public OpsOutboundUrlPolicy(
            OpsOutboundUrlSettings settings,
            OpsOutboundDnsResolver dnsResolver) {
        this(settings, new OpsOutboundAddressPolicy(), dnsResolver);
    }

    OpsOutboundUrlPolicy(
            OpsOutboundUrlSettings settings,
            OpsOutboundAddressPolicy addressPolicy,
            OpsOutboundDnsResolver dnsResolver) {
        this.settings = settings == null ? OpsOutboundUrlSettings.defaults() : settings;
        this.addressPolicy = addressPolicy;
        this.dnsResolver = dnsResolver == null ? new JvmOpsOutboundDnsResolver() : dnsResolver;
    }

    public URI validate(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_URL_REQUIRED");
        }
        URI uri = parse(value);
        String host = uri.getHost();
        if (!StringUtils.hasText(host)
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_URL_INVALID");
        }
        boolean explicitLoopback = addressPolicy.explicitLoopback(host);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !(settings.allowLoopback()
                && explicitLoopback
                && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_HTTPS_REQUIRED");
        }
        if (!addressPolicy.hostAllowed(host, settings)) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_HOST_NOT_ALLOWED");
        }
        InetAddress[] addresses = resolve(host);
        if (addresses.length == 0) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_DNS_UNRESOLVED");
        }
        for (InetAddress address : addresses) {
            if (addressPolicy.blocked(address)
                    && !(settings.allowLoopback()
                    && explicitLoopback
                    && address.isLoopbackAddress())) {
                throw new IllegalArgumentException("CHANNEL_OUTBOUND_PRIVATE_ADDRESS_BLOCKED");
            }
        }
        return uri;
    }

    private URI parse(String value) {
        try {
            return URI.create(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_URL_INVALID", e);
        }
    }

    private InetAddress[] resolve(String host) {
        try {
            return dnsResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("CHANNEL_OUTBOUND_DNS_UNRESOLVED", e);
        }
    }
}
