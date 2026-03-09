package cn.lgs.orbisops.domain.memory.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Semantic-memory metadata, scope and ranking policy independent from vector and lexical engines. */
public class SemanticMemoryPolicy {

    private final MemoryContentHashPolicy hashPolicy;

    public SemanticMemoryPolicy(MemoryContentHashPolicy hashPolicy) {
        this.hashPolicy = hashPolicy == null ? new MemoryContentHashPolicy() : hashPolicy;
    }

    public Map<String, Object> baseMetadata(String sessionId, String userId, String memoryKind) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memory_type", "ops_chat");
        metadata.put("memory_kind", value(memoryKind));
        metadata.put("knowledge", "ops-chat-memory");
        metadata.put("session_id", value(sessionId));
        metadata.put("user_id", value(userId));
        metadata.put("source", "ops_memory_facade");
        return metadata;
    }

    public boolean inScope(Map<String, Object> metadata, String sessionId, String userId) {
        if (metadata == null) {
            return false;
        }
        String documentUserId = value(metadata.get("user_id"));
        return "ops_chat".equals(value(metadata.get("memory_type")))
                && "message".equals(value(metadata.get("memory_kind")))
                && value(sessionId).equals(value(metadata.get("session_id")))
                && (!hasText(userId) || value(userId).equals(documentUserId) || !hasText(documentUserId))
                && active(metadata);
    }

    public boolean active(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return true;
        }
        Object supersededBy = metadata.get("superseded_by");
        if (supersededBy != null && hasText(String.valueOf(supersededBy))) {
            return false;
        }
        Object status = metadata.get("memory_status");
        if (status == null) {
            status = metadata.get("status");
        }
        if (status == null) {
            return true;
        }
        String normalized = String.valueOf(status).trim().toUpperCase();
        return !"SUPERSEDED".equals(normalized)
                && !"DELETED".equals(normalized)
                && !"DISABLED".equals(normalized);
    }

    public double rrfScore(int rank) {
        return 1.0D / (60D + Math.max(1, rank));
    }

    public double score(Map<String, Object> metadata,
                        long currentTurn,
                        boolean recencyAware,
                        double recencyHalfLifeTurns) {
        Map<String, Object> safe = metadata == null ? Map.of() : metadata;
        double relevance;
        if (safe.containsKey("memory_rrf_score")) {
            relevance = parseDouble(safe.get("memory_rrf_score"), 0.5D);
        } else if (safe.containsKey("distance")) {
            relevance = parseDouble(safe.get("distance"), 0.5D);
            relevance = 1.0D - Math.max(0D, Math.min(1D, relevance));
        } else {
            relevance = parseDouble(safe.get("score"), 0.5D);
        }
        double importance = importance(safe.get("importance")).doubleValue();
        return relevance
                * recencyWeight(safe, currentTurn, recencyAware, recencyHalfLifeTurns)
                * Math.max(0.1D, Math.min(1.5D, importanceWeight(importance)))
                * kindWeight(safe.get("memory_kind"));
    }

    public long turnIndex(Map<String, Object> metadata) {
        return metadata == null ? 0L : parseLong(metadata.get("turn_index"));
    }

    public String documentKey(String id, String content, Map<String, Object> metadata) {
        if (hasText(id)) {
            return id;
        }
        Object sourceHash = metadata == null ? null : metadata.get("source_message_hash");
        if (sourceHash != null && hasText(String.valueOf(sourceHash))) {
            return String.valueOf(sourceHash);
        }
        return hashPolicy.stableHash(value(content));
    }

    public BigDecimal importance(Object value) {
        if (value == null) {
            return BigDecimal.valueOf(0.5D);
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return BigDecimal.valueOf(0.5D);
        }
    }

    private double recencyWeight(Map<String, Object> metadata,
                                 long currentTurn,
                                 boolean recencyAware,
                                 double recencyHalfLifeTurns) {
        if (!recencyAware) {
            return 1.0D;
        }
        long turn = parseLong(metadata.get("turn_index"));
        if (turn <= 0) {
            return 0.85D;
        }
        if (currentTurn <= turn) {
            return 1.0D;
        }
        double age = Math.max(0D, currentTurn - turn);
        return 1.0D / (1.0D + age / Math.max(1D, recencyHalfLifeTurns));
    }

    private double importanceWeight(double importance) {
        return 0.75D + Math.max(0D, Math.min(1D, importance)) * 0.5D;
    }

    private double kindWeight(Object kind) {
        String normalized = kind == null ? "" : String.valueOf(kind).toLowerCase();
        if ("preference".equals(normalized)) {
            return 1.08D;
        }
        if ("summary".equals(normalized)) {
            return 0.95D;
        }
        return 1.0D;
    }

    private double parseDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
