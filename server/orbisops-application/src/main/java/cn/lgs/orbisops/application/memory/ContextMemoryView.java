package cn.lgs.orbisops.application.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed context-memory read model selected for a runtime scene. */
public record ContextMemoryView(
        String memoryId,
        String memoryType,
        String scopeType,
        String scopeId,
        String title,
        String summary,
        String content,
        String sourceMessageHash,
        Map<String, Object> attributes) {

    public ContextMemoryView {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
