package cn.lgs.orbisops.trigger.ops.capability;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Typed limits and outbound URL policy for capability import. */
public record OpsCapabilityImportSettings(
        long maxArtifactBytes,
        long maxPackageBytes,
        int maxArtifacts,
        int fetchTimeoutSeconds,
        boolean allowLoopback,
        List<String> allowedHosts) {

    public OpsCapabilityImportSettings {
        maxArtifactBytes = bounded(maxArtifactBytes, 0L, 64L * 1024L * 1024L, 262_144L);
        maxPackageBytes = bounded(maxPackageBytes, 0L, 256L * 1024L * 1024L, 2_097_152L);
        maxArtifacts = bounded(maxArtifacts, 0, 1_000, 32);
        fetchTimeoutSeconds = bounded(fetchTimeoutSeconds, 2, 30, 10);
        allowedHosts = allowedHosts == null
                ? List.of()
                : allowedHosts.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .distinct()
                        .toList();
    }

    public static OpsCapabilityImportSettings defaults() {
        return new OpsCapabilityImportSettings(
                262_144L,
                2_097_152L,
                32,
                10,
                false,
                List.of());
    }

    static OpsCapabilityImportSettings legacyConstructorDefaults() {
        return new OpsCapabilityImportSettings(0L, 0L, 0, 2, false, List.of());
    }

    public static OpsCapabilityImportSettings fromRaw(
            long maxArtifactBytes,
            long maxPackageBytes,
            int maxArtifacts,
            int fetchTimeoutSeconds,
            boolean allowLoopback,
            String allowedHosts) {
        List<String> hosts = Arrays.stream(
                        allowedHosts == null ? new String[0] : allowedHosts.split(","))
                .toList();
        return new OpsCapabilityImportSettings(
                maxArtifactBytes,
                maxPackageBytes,
                maxArtifacts,
                fetchTimeoutSeconds,
                allowLoopback,
                hosts);
    }

    OpsSkillPackageMaterializer.Settings skillPackageSettings() {
        return new OpsSkillPackageMaterializer.Settings(
                maxArtifactBytes,
                maxPackageBytes,
                maxArtifacts);
    }

    boolean hostAllowed(String host) {
        if (allowedHosts.isEmpty()) {
            return true;
        }
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return allowedHosts.stream().anyMatch(pattern -> normalized.equals(pattern)
                || (pattern.startsWith("*.") && normalized.endsWith(pattern.substring(1))));
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private static long bounded(long value, long min, long max, long fallback) {
        return value < min || value > max ? fallback : value;
    }
}
