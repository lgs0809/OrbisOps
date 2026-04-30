package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillSimilarityTextMetricsTest {

    private final OpsSkillSimilarityTextMetrics metrics =
            new OpsSkillSimilarityTextMetrics();

    @Test
    void computesDiceAndJaccardWithStableEmptyBehavior() {
        assertEquals(1D, metrics.dice("订单失败", "订单失败"));
        assertEquals(0D, metrics.dice("", "订单失败"));
        assertEquals(1D / 3D,
                metrics.jaccard(Set.of("routing:", "recipe:"),
                        Set.of("routing:", "evidence:")));
        assertEquals(0D, metrics.jaccard(Set.of(), Set.of("routing:")));
    }

    @Test
    void normalizesContainsAndAbbreviatesText() {
        assertEquals("order recovery", metrics.normalize("  ORDER   Recovery "));
        assertTrue(metrics.containsIgnoreCase("UPDATE_SKILL candidate", "update_skill"));
        assertEquals("abc", metrics.abbreviate("abcdef", 3));
        assertEquals("", metrics.text(null));
    }
}
