package cn.lgs.orbisops.trigger.ops.capability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsCapabilityImportSettingsTest {

    @Test
    void normalizesLimitsAndHostAllowlist() {
        OpsCapabilityImportSettings settings = OpsCapabilityImportSettings.fromRaw(
                -1L,
                300L * 1024L * 1024L,
                2_000,
                60,
                true,
                " API.EXAMPLE.COM, *.Example.org,api.example.com ");

        assertEquals(262_144L, settings.maxArtifactBytes());
        assertEquals(2_097_152L, settings.maxPackageBytes());
        assertEquals(32, settings.maxArtifacts());
        assertEquals(10, settings.fetchTimeoutSeconds());
        assertEquals(List.of("api.example.com", "*.example.org"), settings.allowedHosts());
        assertTrue(settings.hostAllowed("api.example.com"));
        assertTrue(settings.hostAllowed("tools.example.org"));
        assertFalse(settings.hostAllowed("example.net"));
    }

    @Test
    void preservesLegacyDirectConstructorLimits() {
        OpsCapabilityImportSettings settings =
                OpsCapabilityImportSettings.legacyConstructorDefaults();

        assertEquals(0L, settings.maxArtifactBytes());
        assertEquals(0L, settings.maxPackageBytes());
        assertEquals(0, settings.maxArtifacts());
        assertEquals(2, settings.fetchTimeoutSeconds());
        assertFalse(settings.allowLoopback());
    }
}
