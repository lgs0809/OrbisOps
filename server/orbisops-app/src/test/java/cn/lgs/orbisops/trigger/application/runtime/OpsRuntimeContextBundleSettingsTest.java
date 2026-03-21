package cn.lgs.orbisops.trigger.application.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsRuntimeContextBundleSettingsTest {

    @Test
    void shouldBoundSelectedSkillLimit() {
        assertAll(
                () -> assertEquals(1, new OpsRuntimeContextBundleSettings(0).selectedSkillLimit()),
                () -> assertEquals(100, new OpsRuntimeContextBundleSettings(1000).selectedSkillLimit()),
                () -> assertEquals(6, OpsRuntimeContextBundleSettings.defaults().selectedSkillLimit()));
    }
}
