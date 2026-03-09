package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillReleaseStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillReleaseStatusTest {

    @Test
    void permitsOnlyGovernedReleaseTransitions() {
        assertTrue(SkillReleaseStatus.CANARY.canTransitionTo(SkillReleaseStatus.PROMOTING));
        assertTrue(SkillReleaseStatus.CANARY.canTransitionTo(SkillReleaseStatus.ROLLED_BACK));
        assertTrue(SkillReleaseStatus.PROMOTING.canTransitionTo(SkillReleaseStatus.ACTIVE));
        assertTrue(SkillReleaseStatus.ACTIVE.canTransitionTo(SkillReleaseStatus.ROLLING_BACK));
        assertTrue(SkillReleaseStatus.ROLLING_BACK.canTransitionTo(SkillReleaseStatus.ROLLED_BACK));

        assertFalse(SkillReleaseStatus.CANARY.canTransitionTo(SkillReleaseStatus.ACTIVE));
        assertFalse(SkillReleaseStatus.ACTIVE.canTransitionTo(SkillReleaseStatus.ROLLED_BACK));
        assertFalse(SkillReleaseStatus.ROLLED_BACK.canTransitionTo(SkillReleaseStatus.CANARY));
        assertThrows(IllegalStateException.class,
                () -> SkillReleaseStatus.CANARY.requireTransitionTo(SkillReleaseStatus.ACTIVE));
    }

    @Test
    void reconciliationStatusIsDerivedFromInFlightState() {
        assertEquals(SkillReleaseStatus.PROMOTION_UNKNOWN,
                SkillReleaseStatus.PROMOTING.reconciliationRequiredStatus());
        assertEquals(SkillReleaseStatus.ROLLBACK_UNKNOWN,
                SkillReleaseStatus.ROLLING_BACK.reconciliationRequiredStatus());
        assertEquals(SkillReleaseStatus.ACTIVE,
                SkillReleaseStatus.ACTIVE.reconciliationRequiredStatus());
    }

    @Test
    void snapshotCreatesValidatedTransitionCopy() {
        SkillReleaseSnapshot canary = new SkillReleaseSnapshot(
                "release-1", "candidate-1", "project-1", "agent-1", "skill-1",
                SkillReleaseStatus.CANARY, 10, 3, "base-hash", "SHADOW_PASSED",
                0, "", Map.of("source", "test"));

        SkillReleaseSnapshot promoting = canary.transitionTo(SkillReleaseStatus.PROMOTING);

        assertEquals(SkillReleaseStatus.CANARY, canary.status());
        assertEquals(SkillReleaseStatus.PROMOTING, promoting.status());
        assertEquals(canary.releaseId(), promoting.releaseId());
        assertEquals(canary.metadata(), promoting.metadata());
        assertThrows(IllegalStateException.class,
                () -> canary.transitionTo(SkillReleaseStatus.ACTIVE));
    }

    @Test
    void unknownPersistentStatusFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> SkillReleaseStatus.require("future-state"));
    }
}
