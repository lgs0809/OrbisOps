package cn.lgs.orbisops.domain.skill.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stable project/agent/run bucketing policy for Skill canary exposure. */
public class SkillCanarySelectionPolicy {

    public boolean selected(
            String projectId,
            String agentId,
            String runId,
            boolean enabled,
            int canaryPercent) {
        if (!enabled) return false;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((text(projectId)
                            + ":"
                            + text(agentId)
                            + ":"
                            + text(runId)).getBytes(StandardCharsets.UTF_8));
            // Scale the unsigned 32-bit space instead of reducing one byte modulo 100:
            // 256 is not divisible by 100, so the old 10% bucket selected 30/256 identities.
            long bucket = (Byte.toUnsignedLong(hash[0]) << 24)
                    | (Byte.toUnsignedLong(hash[1]) << 16)
                    | (Byte.toUnsignedLong(hash[2]) << 8)
                    | Byte.toUnsignedLong(hash[3]);
            return bucket * 100L < percent(enabled, canaryPercent) * (1L << 32);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Skill Canary hash 计算失败",
                    error);
        }
    }

    public int percent(boolean enabled, int canaryPercent) {
        return enabled ? Math.max(0, Math.min(100, canaryPercent)) : 0;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
