package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.service.SkillMaintenancePolicy;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class SkillMaintenancePolicyTest {
    final SkillMaintenancePolicy policy=new SkillMaintenancePolicy();
    @Test void thresholdsAreStrictAndPatchChecksDoNotRepeatWithoutFiveFurtherPatches() {
        assertFalse(policy.compressionDue(2000,4,0));assertTrue(policy.compressionDue(2001,0,0));
        assertTrue(policy.compressionDue(100,5,0));assertFalse(policy.compressionDue(100,9,5));
        assertTrue(policy.compressionDue(100,10,5));
    }
    @Test void inactivityUsesTheMostRecentUseAndDoesNotTreatFreshUnusedSkillsAsOld() {
        var now=Instant.parse("2026-09-27T00:00:00Z");
        assertFalse(policy.inactivityDue(now,null,now));
        assertFalse(policy.inactivityDue(now.minus(Duration.ofDays(90)).plusSeconds(1),null,now));
        assertTrue(policy.inactivityDue(now.minus(Duration.ofDays(90)),null,now));
        assertFalse(policy.inactivityDue(now.minus(Duration.ofDays(200)),now.minus(Duration.ofDays(1)),now));
    }
}
