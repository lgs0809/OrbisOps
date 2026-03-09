package cn.lgs.orbisops.trigger.application.agentdefinition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentDefinitionRuntimeSettingsTest {

    @Test
    void shouldKeepConfiguredAndEffectiveDefaultIdentitySeparate() {
        OpsAgentDefinitionRuntimeSettings settings = OpsAgentDefinitionRuntimeSettings.forTest(
                "classpath*:agents/*.yml",
                "configured-agent",
                true);

        settings.useEffectiveDefaultAgentId("fallback-agent");

        assertEquals("classpath*:agents/*.yml", settings.locations());
        assertEquals("configured-agent", settings.configuredDefaultAgentId());
        assertEquals("fallback-agent", settings.effectiveDefaultAgentId());
        assertTrue(settings.jdbcEnabled());
    }

    @Test
    void shouldNormalizeBlankConfiguredDefaultAndRejectBlankEffectiveIdentity() {
        OpsAgentDefinitionRuntimeSettings settings = OpsAgentDefinitionRuntimeSettings.forTest(
                "classpath*:agents/*.yaml",
                " ",
                false);

        assertEquals(OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID,
                settings.configuredDefaultAgentId());
        assertEquals(OpsAgentDefinitionDefaults.DEFAULT_AGENT_ID,
                settings.effectiveDefaultAgentId());
        assertFalse(settings.jdbcEnabled());
        assertThrows(IllegalArgumentException.class,
                () -> settings.useEffectiveDefaultAgentId(" "));
    }
}
