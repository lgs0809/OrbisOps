package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsOutboundUrlBoundaryArchitectureTest {

    private static final String CHANNEL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/channel/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/channel/";

    @Test
    void outboundUrlFacadeDelegatesTypedHostsAddressPolicyAndDnsPort() throws IOException {
        String facade = read(CHANNEL + "OpsOutboundUrlPolicy.java");
        String settings = read(CHANNEL + "OpsOutboundUrlSettings.java");
        String addressPolicy = read(CHANNEL + "OpsOutboundAddressPolicy.java");
        String dnsPort = read(CHANNEL + "OpsOutboundDnsResolver.java");
        String dnsAdapter = read(CHANNEL + "JvmOpsOutboundDnsResolver.java");
        String configuration = read(APPLICATION + "OpsOutboundUrlConfiguration.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsOutboundUrlSettings settings")),
                () -> assertTrue(facade.contains("OpsOutboundAddressPolicy addressPolicy")),
                () -> assertTrue(facade.contains("OpsOutboundDnsResolver dnsResolver")),
                () -> assertTrue(facade.contains("public OpsOutboundUrlPolicy()")),
                () -> assertTrue(facade.contains("@Autowired")),
                () -> assertTrue(facade.contains("dnsResolver.resolve(host)")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("InetAddress.getAllByName")),
                () -> assertFalse(facade.contains("Inet4Address")),
                () -> assertFalse(facade.contains("allowedHosts.split")),
                () -> assertTrue(facade.lines().count() <= 110),
                () -> assertTrue(settings.contains("public record OpsOutboundUrlSettings(")),
                () -> assertTrue(settings.contains("Set<String> allowedHosts")),
                () -> assertTrue(addressPolicy.contains("boolean hostAllowed(")),
                () -> assertTrue(addressPolicy.contains("boolean blocked(InetAddress address)")),
                () -> assertTrue(addressPolicy.contains("address instanceof Inet4Address")),
                () -> assertTrue(addressPolicy.contains("address instanceof Inet6Address")),
                () -> assertFalse(addressPolicy.contains("@Component")),
                () -> assertTrue(dnsPort.contains("public interface OpsOutboundDnsResolver")),
                () -> assertTrue(dnsAdapter.contains("InetAddress.getAllByName(host)")),
                () -> assertTrue(configuration.contains("orbisops.channel.webhook.allow-loopback")),
                () -> assertTrue(configuration.contains("orbisops.channel.webhook.allowed-hosts")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
