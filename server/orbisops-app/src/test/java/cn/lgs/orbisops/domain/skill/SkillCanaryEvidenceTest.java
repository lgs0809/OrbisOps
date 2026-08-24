package cn.lgs.orbisops.domain.skill;
import cn.lgs.orbisops.domain.skill.model.SkillCanaryEvidence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillCanaryEvidenceTest {
    @Test void twentyAssignedTasksNeedNineteenVerifiedSuccessesAndCompletedSafetyReview() {
        assertTrue(new SkillCanaryEvidence(20,20,19,0,2,0,1,20).promotable());
        assertFalse(new SkillCanaryEvidence(20,19,19,0,2,0,0,20).promotable());
        assertFalse(new SkillCanaryEvidence(20,20,18,0,2,0,0,20).promotable());
        assertFalse(new SkillCanaryEvidence(20,20,20,1,2,0,0,20).promotable());
        assertFalse(new SkillCanaryEvidence(20,20,20,0,1,0,0,20).promotable());
        assertFalse(new SkillCanaryEvidence(20,20,20,0,2,0,0,0).promotable());
    }
    @Test void safetyAndAttributedDegradationDoNotWaitForTwentySamples() {
        assertEquals("CANARY_SAFETY_VIOLATION",new SkillCanaryEvidence(1,1,1,0,1,1,0,1).isolationReason());
        assertEquals("CANARY_ATTRIBUTED_REGRESSION",new SkillCanaryEvidence(2,2,0,0,2,0,2,2).isolationReason());
        assertFalse(SkillCanaryEvidence.unknown().promotable());
    }
}
