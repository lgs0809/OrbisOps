package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillSemanticPolicyTest {

    private final OpsSkillSemanticPolicy policy = new OpsSkillSemanticPolicy();

    @Test
    void filtersInvalidDocumentsAndAppliesCandidateLimit() {
        List<OpsSkillSemanticMatcher.SkillDocument> bounded = policy.bounded(
                List.of(
                        new OpsSkillSemanticMatcher.SkillDocument("one", "", "first"),
                        new OpsSkillSemanticMatcher.SkillDocument("", "invalid", "missing id"),
                        new OpsSkillSemanticMatcher.SkillDocument("two", "key-two", "second")),
                new OpsSkillSemanticSettings(true, 1, 128));

        assertEquals(1, bounded.size());
        assertEquals("one", bounded.get(0).id());
        assertTrue(policy.cacheKey(bounded.get(0)).startsWith("one:"));
    }

    @Test
    void cosineIsClampedAndRejectsInvalidDimensions() {
        assertEquals(1D, policy.cosine(new float[]{1F, 0F}, new float[]{1F, 0F}));
        assertEquals(0D, policy.cosine(new float[]{1F, 0F}, new float[]{0F, 1F}));
        assertEquals(0D, policy.cosine(new float[]{1F}, new float[]{1F, 0F}));
    }
}
