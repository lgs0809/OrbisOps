package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsExternalLocalProviderSettingsTest {

    @Test
    void productionCatalogKeepsMcpAndInternalToolsButRemovesExternalLocalAndGenericCommands() {
        OpsExternalLocalProviderSettings providers =
                OpsExternalLocalProviderSettings.productionDisabled();
        OpsToolsetRegistry registry = new OpsToolsetRegistry(
                new OpsBuiltInToolsetCatalog(),
                new OpsToolsetDefinitionCopier(),
                providers);

        Set<String> ids = registry.listBuiltInToolsets().stream()
                .map(OpsToolsetDefinition::getToolsetId)
                .collect(Collectors.toSet());

        assertFalse(ids.contains("observability.prometheus"));
        assertFalse(ids.contains("observability.logs"));
        assertFalse(ids.contains("db.mysql.readonly"));
        assertFalse(ids.contains("cache.redis.readonly"));
        assertFalse(ids.contains("container.docker.readonly"));
        assertFalse(ids.contains("db.mysql.change"));
        assertFalse(ids.contains("cache.redis.change"));
        assertFalse(ids.contains("infra.k8s.remediation"));
        assertFalse(ids.contains("job.platform.execute"));
        assertTrue(ids.contains("observability.traces"));
        assertTrue(ids.contains("infra.k8s.readonly"));
        assertTrue(ids.contains("local.logs"));
        assertTrue(ids.contains("change_package"));
        assertTrue(ids.contains(UpdateAlertThresholdPolicy.TOOLSET_ID));
        assertTrue(registry.rawBuiltInToolsets().stream()
                .map(OpsToolsetDefinition::getToolsetId)
                .anyMatch("db.mysql.change"::equals));
        assertEquals("UP", providers.readiness().get("status"));
        OpsToolRuntimeReadiness readiness =
                new OpsToolRuntimeReadiness(providers, registry);
        assertEquals("UP", readiness.readiness().get("status"));
        assertEquals(true, readiness.readiness().get("businessWriteToolReady"));
        assertEquals(java.util.List.of("INTERNAL", "MCP"), readiness.readiness().get("productionToolBindings"));
        assertEquals(Set.of(), Set.copyOf((java.util.List<String>)
                readiness.readiness().get("unsafeToolsetsExposed")));
    }

    @Test
    void productionFailsClosedWhenAnyExternalLocalProviderIsOverriddenOn() {
        OpsExternalLocalProviderSettings unsafe = new OpsExternalLocalProviderSettings(
                "PRODUCTION",
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                true);

        assertFalse(unsafe.productionSafe());
        assertEquals("DOWN", unsafe.readiness().get("status"));
        assertEquals("PRODUCTION_TOOL_PROFILE_UNSAFE", unsafe.readiness().get("reason"));
    }

}
