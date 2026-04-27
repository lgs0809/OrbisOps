package cn.lgs.orbisops.trigger.ops.channel;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsOutboundAddressPolicyTest {

    private final OpsOutboundAddressPolicy policy = new OpsOutboundAddressPolicy();

    @Test
    void supportsExactAndWildcardHostPatterns() {
        OpsOutboundUrlSettings settings = OpsOutboundUrlSettings.fromRaw(
                false,
                "hooks.example.com,*.notify.example.net");

        assertTrue(policy.hostAllowed("hooks.example.com", settings));
        assertTrue(policy.hostAllowed("a.notify.example.net", settings));
        assertFalse(policy.hostAllowed("notify.example.net", settings));
        assertFalse(policy.hostAllowed("example.com", settings));
    }

    @Test
    void blocksPrivateCgnatMulticastAndIpv6LocalRanges() throws Exception {
        assertTrue(policy.blocked(InetAddress.getByName("10.0.0.1")));
        assertTrue(policy.blocked(InetAddress.getByName("100.64.0.1")));
        assertTrue(policy.blocked(InetAddress.getByName("224.0.0.1")));
        assertTrue(policy.blocked(InetAddress.getByName("fc00::1")));
        assertTrue(policy.blocked(InetAddress.getByName("fe80::1")));
        assertFalse(policy.blocked(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void onlyExplicitLoopbackLiteralsQualifyForHttpDevelopmentException() {
        assertTrue(policy.explicitLoopback("localhost"));
        assertTrue(policy.explicitLoopback("127.0.0.1"));
        assertTrue(policy.explicitLoopback("::1"));
        assertFalse(policy.explicitLoopback("local.example.com"));
    }
}
