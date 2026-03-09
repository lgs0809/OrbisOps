package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillEvolutionSettingsTest {

    @Test
    void normalizesWorkerBoundsAndSeparatesSpringFromLegacyDefaults() {
        OpsSkillEvolutionSettings normalized =
                new OpsSkillEvolutionSettings(true, true, 0, 99, 100L);

        assertEquals(5, normalized.batchSize());
        assertEquals(3, normalized.maxAttempts());
        assertEquals(60_000L, normalized.fixedDelayMillis());
        assertTrue(OpsSkillEvolutionSettings.defaults().triggerEnabled());
        assertFalse(OpsSkillEvolutionSettings.defaults().workerEnabled());
        assertFalse(OpsSkillEvolutionSettings.legacyConstructorDefaults().triggerEnabled());
        assertFalse(OpsSkillEvolutionSettings.legacyConstructorDefaults().workerEnabled());
    }
}
