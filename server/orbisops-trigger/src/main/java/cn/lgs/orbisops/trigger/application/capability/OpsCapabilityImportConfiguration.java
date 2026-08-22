package cn.lgs.orbisops.trigger.application.capability;

import cn.lgs.orbisops.trigger.ops.capability.OpsCapabilityImportSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsCapabilityImportConfiguration {

    @Bean
    public OpsCapabilityImportSettings opsCapabilityImportSettings(
            @Value("${orbisops.capability-import.max-artifact-bytes:262144}") long maxArtifactBytes,
            @Value("${orbisops.capability-import.max-package-bytes:2097152}") long maxPackageBytes,
            @Value("${orbisops.capability-import.max-artifacts:32}") int maxArtifacts,
            @Value("${orbisops.capability-import.fetch-timeout-seconds:10}") int fetchTimeoutSeconds,
            @Value("${orbisops.capability-import.allow-loopback:false}") boolean allowLoopback,
            @Value("${orbisops.capability-import.allowed-hosts:}") String allowedHosts) {
        return OpsCapabilityImportSettings.fromRaw(
                maxArtifactBytes,
                maxPackageBytes,
                maxArtifacts,
                fetchTimeoutSeconds,
                allowLoopback,
                allowedHosts);
    }
}
