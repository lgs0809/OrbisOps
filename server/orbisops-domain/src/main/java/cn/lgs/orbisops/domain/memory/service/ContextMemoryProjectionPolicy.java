package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;

import java.util.Map;
import java.util.Optional;

/** Domain policy for projecting extracted cold memory items into durable Context Memory definitions. */
public class ContextMemoryProjectionPolicy {

    private final ContextMemoryDefinitionPolicy definitionPolicy;
    private final MemoryContentHashPolicy contentHashPolicy;

    public ContextMemoryProjectionPolicy() {
        this(new ContextMemoryDefinitionPolicy(), new MemoryContentHashPolicy());
    }

    public ContextMemoryProjectionPolicy(ContextMemoryDefinitionPolicy definitionPolicy,
                                         MemoryContentHashPolicy contentHashPolicy) {
        this.definitionPolicy = definitionPolicy == null
                ? new ContextMemoryDefinitionPolicy()
                : definitionPolicy;
        this.contentHashPolicy = contentHashPolicy == null
                ? new MemoryContentHashPolicy()
                : contentHashPolicy;
    }

    public Optional<ContextMemorySnapshot> project(ColdMemoryItemSnapshot item) {
        if (item == null || !hasText(item.content())) return Optional.empty();
        String memoryType = definitionPolicy.normalizeMemoryType(item.memoryType(), false);
        if (!hasText(memoryType)) return Optional.empty();
        Map<String, Object> metadata = item.metadata() == null ? Map.of() : item.metadata();
        String scopeType = definitionPolicy.normalizeScope(text(metadata.get("scopeType")), false);
        if (!hasText(scopeType)) {
            scopeType = memoryType.startsWith("USER_") ? "USER" : "PROJECT";
        }
        definitionPolicy.validateScopeAndType(scopeType, memoryType);
        String scopeId = "USER".equals(scopeType)
                ? text(item.userId())
                : text(metadata.get("projectId"));
        if (!hasText(scopeId)) return Optional.empty();
        String content = item.content();
        String memoryId = "ctx-mem-" + contentHashPolicy.stableHash(
                scopeType + ":" + scopeId + ":" + memoryType + ":" + content);
        return Optional.of(new ContextMemorySnapshot(
                null,
                memoryId,
                scopeType,
                scopeId,
                memoryType,
                text(metadata.get("title"), memoryType),
                text(metadata.get("summary"), abbreviate(content, 240)),
                content,
                item.tagsJson() == null ? "" : item.tagsJson(),
                definitionPolicy.normalizeStatus(text(metadata.get("memory_status"), "ACTIVE"), true),
                definitionPolicy.normalizeConfidence(item.importance()),
                text(metadata.get("source"), "memory_extractor"),
                text(item.sessionId()),
                text(item.sourceMessageHash()),
                text(item.userId()),
                "",
                "",
                ""));
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String text(Object value, String fallback) {
        String result = text(value);
        return hasText(result) ? result : fallback;
    }
}
