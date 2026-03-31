package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Spring compatibility facade for MCP transport security. */
@Service
public class OpsMcpTransportSecurityPolicy {

    private final OpsMcpTransportSecuritySettings settings;
    private final OpsMcpTransportAdmissionPolicy admissionPolicy;
    private final OpsMcpTransportSecretFilter secretFilter;

    public OpsMcpTransportSecurityPolicy() {
        this(OpsMcpTransportSecuritySettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsMcpTransportSecurityPolicy(OpsMcpTransportSecuritySettings settings) {
        this(
                settings,
                new OpsMcpTransportAdmissionPolicy(),
                new OpsMcpTransportSecretFilter());
    }

    OpsMcpTransportSecurityPolicy(
            OpsMcpTransportSecuritySettings settings,
            OpsMcpTransportAdmissionPolicy admissionPolicy,
            OpsMcpTransportSecretFilter secretFilter) {
        this.settings = settings == null
                ? OpsMcpTransportSecuritySettings.defaults()
                : settings;
        this.admissionPolicy = admissionPolicy;
        this.secretFilter = secretFilter;
    }

    public String normalizeTransport(String transport) {
        return admissionPolicy.normalizeTransport(transport);
    }

    public int normalizeTimeout(Integer timeoutSeconds) {
        return admissionPolicy.normalizeTimeout(timeoutSeconds, settings);
    }

    public void assertTransportAllowed(OpsMcpServerConfig config, String transport) {
        admissionPolicy.assertTransportAllowed(config, transport, settings);
    }

    public void assertStdioAllowed(OpsMcpServerConfig config, String commandValue) {
        admissionPolicy.assertStdioAllowed(config, commandValue, settings);
    }

    public void assertArgsAllowed(OpsMcpServerConfig config, List<String> args) {
        admissionPolicy.assertArgsAllowed(serverName(config), args, settings);
    }

    public void assertRemoteAddressAllowed(OpsMcpServerConfig config, String baseUri) {
        admissionPolicy.assertRemoteAddressAllowed(serverName(config), baseUri, settings);
    }

    public Map<String, String> safeEnv(
            OpsMcpServerConfig config,
            Map<String, String> source) {
        return secretFilter.safeEnv(serverName(config), source, settings);
    }

    public Map<String, String> safeHeaders(
            OpsMcpServerConfig config,
            Map<String, String> source) {
        return secretFilter.safeHeaders(serverName(config), source, settings);
    }

    private String serverName(OpsMcpServerConfig config) {
        return config == null || config.getName() == null ? "" : config.getName();
    }
}
