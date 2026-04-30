package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillToolSettingsTest {

    @Test
    void parsesDeduplicatesAndFreezesConfiguredLocations() {
        OpsSkillToolSettings settings = OpsSkillToolSettings.fromRaw(
                true,
                " classpath:/skills, ./data/shared,classpath:/skills ",
                " ./data/skills ",
                true);

        assertTrue(settings.enabled());
        assertEquals(List.of("classpath:/skills", "./data/shared"),
                settings.configuredLocations());
        assertEquals("./data/skills", settings.editableLocation());
        assertTrue(settings.editableAutoInit());
    }

    @Test
    void separatesSpringDefaultsFromLegacyDisabledConstructor() {
        OpsSkillToolSettings defaults = OpsSkillToolSettings.defaults();
        OpsSkillToolSettings legacy = OpsSkillToolSettings.legacyConstructorDefaults();

        assertTrue(defaults.enabled());
        assertEquals(List.of("classpath:/skills"), defaults.configuredLocations());
        assertEquals("./data/skills", defaults.editableLocation());
        assertTrue(defaults.editableAutoInit());
        assertFalse(legacy.enabled());
        assertTrue(legacy.configuredLocations().isEmpty());
        assertEquals("", legacy.editableLocation());
        assertFalse(legacy.editableAutoInit());
    }
}
