package cn.lgs.orbisops.domain.skill.service;

import java.time.Duration;
import java.time.Instant;

/** Maintenance is a check, never authority to expand a method or remove an inactive Skill. */
public final class SkillMaintenancePolicy {
    public boolean compressionDue(int bodyTokens, int patches, int checkedPatches) {
        return bodyTokens > 2000 || patches / 5 > checkedPatches / 5;
    }

    public boolean inactivityDue(Instant created, Instant lastUse, Instant now) {
        if (created == null || now == null) throw new IllegalArgumentException("SKILL_MAINTENANCE_TIME_REQUIRED");
        Instant baseline = lastUse != null && lastUse.isAfter(created) ? lastUse : created;
        return !baseline.plus(Duration.ofDays(90)).isAfter(now);
    }

}
