package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsSkillSimilaritySettingsTest {

    @Test
    void normalizesInvalidThresholdAndPreservesLegacyZero() {
        assertEquals(0.72D,
                new OpsSkillSimilaritySettings(Double.NaN).threshold());
        assertEquals(0.72D,
                new OpsSkillSimilaritySettings(1.1D).threshold());
        assertEquals(0.72D,
                OpsSkillSimilaritySettings.defaults().threshold());
        assertEquals(0D,
                OpsSkillSimilaritySettings.legacyConstructorDefaults().threshold());
    }
}
