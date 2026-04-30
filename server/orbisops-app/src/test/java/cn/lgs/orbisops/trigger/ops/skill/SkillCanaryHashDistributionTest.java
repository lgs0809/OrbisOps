package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.domain.skill.service.SkillCanarySelectionPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillCanaryHashDistributionTest {
    private final SkillCanarySelectionPolicy policy = new SkillCanarySelectionPolicy();

    @Test void tenPercentDoesNotInheritTheOneByteModuloBias() {
        int selected = 0;
        for (int i = 0; i < 100_000; i++) {
            if (policy.selected("project", "agent", "task-" + i, true, 10)) selected++;
        }
        assertTrue(selected >= 9700 && selected <= 10300, "selected=" + selected);
    }

    @Test void retriesAndBoundaryPercentagesStayDeterministic() {
        boolean first = policy.selected("p", "a", "stable-task", true, 10);
        for (int i = 0; i < 20; i++) assertEquals(first, policy.selected("p", "a", "stable-task", true, 10));
        assertFalse(policy.selected("p", "a", "stable-task", false, 100));
        assertFalse(policy.selected("p", "a", "stable-task", true, 0));
        assertTrue(policy.selected("p", "a", "stable-task", true, 100));
    }
}
