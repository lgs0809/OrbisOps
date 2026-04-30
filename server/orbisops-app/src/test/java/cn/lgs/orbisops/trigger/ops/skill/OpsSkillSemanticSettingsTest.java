package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillSemanticSettingsTest {

    @Test
    void normalizesCandidateAndCacheBounds() {
        OpsSkillSemanticSettings normalized =
                new OpsSkillSemanticSettings(true, 0, 100);

        assertTrue(normalized.semanticEnabled());
        assertEquals(64, normalized.candidateLimit());
        assertEquals(2_048, normalized.cacheSize());
        assertEquals(128,
                new OpsSkillSemanticSettings(true, 128, 128).candidateLimit());
    }
}
