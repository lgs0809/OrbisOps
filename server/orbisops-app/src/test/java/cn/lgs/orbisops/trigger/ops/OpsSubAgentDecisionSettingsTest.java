package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSubAgentDecisionSettingsTest {

    @Test
    void distinguishesSpringDefaultsFromLegacyConstructorDefaults() {
        OpsSubAgentDecisionSettings defaults = OpsSubAgentDecisionSettings.defaults();
        OpsSubAgentDecisionSettings legacy = OpsSubAgentDecisionSettings.legacyConstructorDefaults();

        assertTrue(defaults.decisionLlmEnabled());
        assertTrue(defaults.reflectionLlmEnabled());
        assertFalse(legacy.decisionLlmEnabled());
        assertFalse(legacy.reflectionLlmEnabled());
    }
}
