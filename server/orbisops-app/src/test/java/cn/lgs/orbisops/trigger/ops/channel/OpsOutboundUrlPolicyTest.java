package cn.lgs.orbisops.trigger.ops.channel;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsOutboundUrlPolicyTest {

    @Test
    void privateAndLoopbackAddressesAreBlockedByDefault() {
        OpsOutboundUrlPolicy policy = policy(false, "");

        assertEquals("CHANNEL_OUTBOUND_PRIVATE_ADDRESS_BLOCKED",
                assertThrows(IllegalArgumentException.class, () -> policy.validate("https://127.0.0.1/hook")).getMessage());
        assertEquals("CHANNEL_OUTBOUND_HTTPS_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> policy.validate("http://localhost/hook")).getMessage());
    }

    @Test
    void explicitLoopbackCanOnlyBeEnabledForLocalDevelopment() {
        OpsOutboundUrlPolicy policy = policy(true, "");

        assertEquals("127.0.0.1", policy.validate("http://127.0.0.1:9999/hook").getHost());
    }

    @Test
    void hostAllowlistAndEmbeddedCredentialsAreFailClosed() {
        OpsOutboundUrlPolicy policy = policy(false, "hooks.example.com");

        assertEquals("CHANNEL_OUTBOUND_HOST_NOT_ALLOWED",
                assertThrows(IllegalArgumentException.class, () -> policy.validate("https://example.net/hook")).getMessage());
        assertEquals("CHANNEL_OUTBOUND_URL_INVALID",
                assertThrows(IllegalArgumentException.class, () -> policy.validate("https://user:pass@hooks.example.com/hook")).getMessage());
    }

    @Test
    void unresolvedAndEmptyDnsResultsUseStableFailureCode() {
        OpsOutboundUrlSettings settings = OpsOutboundUrlSettings.fromRaw(false, "hooks.example.com");
        OpsOutboundUrlPolicy unresolved = new OpsOutboundUrlPolicy(
                settings,
                host -> { throw new UnknownHostException(host); });
        OpsOutboundUrlPolicy empty = new OpsOutboundUrlPolicy(
                settings,
                host -> new InetAddress[0]);

        assertEquals("CHANNEL_OUTBOUND_DNS_UNRESOLVED",
                assertThrows(IllegalArgumentException.class,
                        () -> unresolved.validate("https://hooks.example.com/hook")).getMessage());
        assertEquals("CHANNEL_OUTBOUND_DNS_UNRESOLVED",
                assertThrows(IllegalArgumentException.class,
                        () -> empty.validate("https://hooks.example.com/hook")).getMessage());
    }

    private OpsOutboundUrlPolicy policy(boolean allowLoopback, String allowedHosts) {
        return new OpsOutboundUrlPolicy(
                OpsOutboundUrlSettings.fromRaw(allowLoopback, allowedHosts),
                new JvmOpsOutboundDnsResolver());
    }
}
