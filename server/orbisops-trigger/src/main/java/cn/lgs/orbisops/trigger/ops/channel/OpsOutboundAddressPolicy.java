package cn.lgs.orbisops.trigger.ops.channel;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/** Pure host-pattern, loopback, and private-address policy for outbound webhooks. */
final class OpsOutboundAddressPolicy {

    boolean hostAllowed(String host, OpsOutboundUrlSettings settings) {
        OpsOutboundUrlSettings effective = effective(settings);
        if (effective.allowedHosts().isEmpty()) {
            return true;
        }
        String normalized = OpsOutboundUrlSettings.normalize(host);
        for (String pattern : effective.allowedHosts()) {
            if (normalized.equals(pattern)
                    || (pattern.startsWith("*.")
                    && normalized.endsWith(pattern.substring(1)))) {
                return true;
            }
        }
        return false;
    }

    boolean explicitLoopback(String host) {
        String normalized = OpsOutboundUrlSettings.normalize(host);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    boolean blocked(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
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

    private OpsOutboundUrlSettings effective(OpsOutboundUrlSettings settings) {
        return settings == null ? OpsOutboundUrlSettings.defaults() : settings;
    }
}
