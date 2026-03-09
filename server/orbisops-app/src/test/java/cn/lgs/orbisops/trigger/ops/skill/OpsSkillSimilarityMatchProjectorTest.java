package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillSimilarityMatchProjectorTest {

    private final OpsSkillSimilarityMatchProjector projector =
            new OpsSkillSimilarityMatchProjector();

    @Test
    void recognizesAllFrozenRepresentationsAndProjectsRoundedView() {
        assertTrue(projector.isFrozen(Map.of("status", "FROZEN")));
        assertTrue(projector.isFrozen(Map.of("updateMode", "frozen")));
        assertTrue(projector.isFrozen(Map.of("frozen", true)));
        assertFalse(projector.isFrozen(Map.of("status", "ACTIVE")));

        Map<String, Object> view = projector.view(
                new OpsSkillSimilarityMatchProjector.Match(
                        Map.of("skillId", "order-recovery"),
                        0.876543D),
                "BEST_PROJECT_MATCH");

        assertEquals("order-recovery", view.get("skillId"));
        assertEquals(0.8765D, view.get("similarity"));
        assertEquals("BEST_PROJECT_MATCH", view.get("similarityReason"));
    }
}
