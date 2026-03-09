package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryDraft;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;

import java.time.Duration;
import java.time.Instant;

/** Domain rules for governed explicit-memory creation, versioning, lifecycle and runtime selection. */
public class GovernedMemoryPolicy {

    private static final int MAX_LOGICAL_KEY_LENGTH = 96;
    private static final int MAX_RUNTIME_LIMIT = 50;

    public GovernedMemoryDraft draft(
            String scopeType,
            String scopeId,
            String memoryType,
            String content,
            String normalizedContent,
            String logicalKey,
            String sourceType,
            boolean verified,
            double confidence,
            String riskLevel) {
        MemoryScope scope = MemoryScope.require(scopeType);
        MemoryType type = MemoryType.require(memoryType);
        if (!type.persistable()) {
            throw new IllegalArgumentException("MEMORY_TYPE_NOT_PERSISTABLE");
        }
        String originalContent = required(content, "MEMORY_CONTENT_REQUIRED");
        String normalized = hasText(normalizedContent)
                ? normalizedContent.trim()
                : normalizeContent(originalContent);
        String key = hasText(logicalKey)
                ? logicalKey.trim()
                : abbreviate(normalized, MAX_LOGICAL_KEY_LENGTH);
        return new GovernedMemoryDraft(
                scope,
                scopeId,
                type,
                originalContent,
                normalized,
                key,
                hasText(sourceType) ? sourceType.trim() : "USER_ASSERTED",
                verified,
                normalizeConfidence(confidence),
                hasText(riskLevel) ? riskLevel.trim() : "LOW");
    }

    public int nextVersion(Integer currentVersion) {
        return currentVersion == null ? 1 : Math.max(0, currentVersion) + 1;
    }

    public String status(boolean conflict) {
        return conflict ? "CONFLICT" : "ACTIVE";
    }

    public Instant expiresAt(MemoryType type, Instant now, Duration defaultTtl) {
        if (type != MemoryType.SESSION_CONTEXT) {
            return null;
        }
        Instant current = now == null ? Instant.now() : now;
        Duration ttl = defaultTtl == null ? Duration.ofHours(24) : defaultTtl;
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("MEMORY_TTL_INVALID");
        }
        return current.plus(ttl);
    }

    public int runtimeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_RUNTIME_LIMIT));
    }

    public void requireProjectFact(MemoryType type) {
        if (type != MemoryType.PROJECT_FACT) {
            throw new IllegalArgumentException("只有 PROJECT_FACT 可以升级为 verified");
        }
    }

    public String normalizeContent(String content) {
        return required(content, "MEMORY_CONTENT_REQUIRED")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double normalizeConfidence(double confidence) {
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            return 0.6D;
        }
        return Math.max(0.0D, Math.min(confidence, 1.0D));
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String required(String value, String code) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
