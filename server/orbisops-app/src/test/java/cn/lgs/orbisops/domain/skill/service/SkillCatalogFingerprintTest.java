package cn.lgs.orbisops.domain.skill.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SkillCatalogFingerprintTest {

    @Test
    void fingerprintIsStableAndChangesWithCatalogContent() {
        String first = fingerprint("Slow SQL", "# Steps\n- EXPLAIN", 3, "ACTIVE", "AUTO");
        String same = fingerprint("slow-sql", "# Steps\n- EXPLAIN", 3, "ACTIVE", "AUTO");
        String changed = fingerprint("slow-sql", "# Steps\n- SHOW PROCESSLIST", 3, "ACTIVE", "AUTO");

        assertEquals(64, first.length());
        assertEquals(first, same);
        assertNotEquals(first, changed);
    }

    @Test
    void legacyFallbackAndTrimmingSemanticsRemainStable() {
        String fallback = fingerprint("slow-sql", "  # Steps  ", 0, "UNKNOWN", "UNKNOWN");
        String normalized = fingerprint("slow-sql", "# Steps", 1, "ENABLED", "AUTO");

        assertEquals(normalized, fallback);
    }

    private String fingerprint(String skillId,
                               String content,
                               int version,
                               String status,
                               String updateMode) {
        return SkillCatalogFingerprint.sha256(
                "PROJECT", "demo-project", skillId, "Slow SQL", "desc", content,
                version, status, updateMode, true, true);
    }
}
