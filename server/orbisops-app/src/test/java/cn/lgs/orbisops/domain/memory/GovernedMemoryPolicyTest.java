package cn.lgs.orbisops.domain.memory;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryDraft;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import cn.lgs.orbisops.domain.memory.service.GovernedMemoryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GovernedMemoryPolicyTest {

    private final GovernedMemoryPolicy policy = new GovernedMemoryPolicy();

    @Test
    void normalizesTypedDraftAndAppliesDefaults() {
        GovernedMemoryDraft draft = policy.draft(
                " project ",
                " demo-project ",
                " project_fact ",
                "  MySQL   主库  ",
                "",
                "",
                "",
                true,
                Double.NaN,
                "");

        assertEquals(MemoryScope.PROJECT, draft.scope());
        assertEquals("demo-project", draft.scopeId());
        assertEquals(MemoryType.PROJECT_FACT, draft.type());
        assertEquals("MySQL   主库", draft.content());
        assertEquals("MySQL 主库", draft.normalizedContent());
        assertEquals("MySQL 主库", draft.logicalKey());
        assertEquals("USER_ASSERTED", draft.sourceType());
        assertEquals(true, draft.verified());
        assertEquals(0.6D, draft.confidence());
        assertEquals("LOW", draft.riskLevel());
    }

    @Test
    void preservesExplicitNormalizedLogicalSourceAndRiskValues() {
        GovernedMemoryDraft draft = policy.draft(
                "USER",
                "u1",
                "USER_PREFERENCE",
                "原始内容",
                " normalized content ",
                " logical-key ",
                " IMPORTED ",
                false,
                0.82D,
                " MEDIUM ");

        assertEquals("原始内容", draft.content());
        assertEquals("normalized content", draft.normalizedContent());
        assertEquals("logical-key", draft.logicalKey());
        assertEquals("IMPORTED", draft.sourceType());
        assertEquals(0.82D, draft.confidence());
        assertEquals("MEDIUM", draft.riskLevel());
    }

    @Test
    void capsDefaultLogicalKeyAndConfidence() {
        String content = "x".repeat(120);
        GovernedMemoryDraft draft = policy.draft(
                "SESSION", "s1", "SESSION_CONTEXT", content,
                "", "", "", false, 2.0D, "");

        assertEquals(96, draft.logicalKey().length());
        assertEquals(1.0D, draft.confidence());
    }

    @Test
    void ownsVersionStatusTtlVerificationAndRuntimeLimitRules() {
        Instant now = Instant.parse("2026-07-22T01:00:00Z");

        assertEquals(1, policy.nextVersion(null));
        assertEquals(3, policy.nextVersion(2));
        assertEquals("ACTIVE", policy.status(false));
        assertEquals("CONFLICT", policy.status(true));
        assertNull(policy.expiresAt(MemoryType.PROJECT_FACT, now, Duration.ofHours(1)));
        assertEquals(now.plus(Duration.ofHours(1)),
                policy.expiresAt(MemoryType.SESSION_CONTEXT, now, Duration.ofHours(1)));
        assertEquals(1, policy.runtimeLimit(0));
        assertEquals(20, policy.runtimeLimit(20));
        assertEquals(50, policy.runtimeLimit(100));
        policy.requireProjectFact(MemoryType.PROJECT_FACT);
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireProjectFact(MemoryType.PROJECT_CONTEXT));
        assertThrows(IllegalArgumentException.class,
                () -> policy.expiresAt(MemoryType.SESSION_CONTEXT, now, Duration.ZERO));
    }

    @Test
    void rejectsUnknownScopeTypeAndMissingContent() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.draft("GLOBAL", "x", "PROJECT_FACT", "content",
                        "", "", "", false, 0.6D, ""));
        assertThrows(IllegalArgumentException.class,
                () -> policy.draft("PROJECT", "p1", "UNCLASSIFIED", "content",
                        "", "", "", false, 0.6D, ""));
        assertThrows(IllegalArgumentException.class,
                () -> policy.draft("PROJECT", "p1", "PROJECT_FACT", " ",
                        "", "", "", false, 0.6D, ""));
    }
}
