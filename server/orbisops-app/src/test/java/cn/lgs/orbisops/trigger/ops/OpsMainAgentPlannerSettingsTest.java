package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainAgentPlannerSettingsTest {

    @Test
    void normalizesModeAndRecognizesAllSourcesAliases() {
        assertEquals("smart", new OpsMainAgentPlannerSettings(true, "  ").mode());
        assertTrue(new OpsMainAgentPlannerSettings(true, " ALL_SOURCES ").allSourcesMode());
        assertTrue(new OpsMainAgentPlannerSettings(true, "all-tools").allSourcesMode());
        assertFalse(new OpsMainAgentPlannerSettings(false, "smart").allSourcesMode());
    }
}
