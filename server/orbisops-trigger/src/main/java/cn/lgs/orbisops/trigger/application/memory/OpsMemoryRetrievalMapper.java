package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryView;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;

import java.util.List;
import java.util.Map;

/** Anti-corruption mapper for runtime retrieval read models. */
public class OpsMemoryRetrievalMapper {

    public MemoryMessageView view(OpsMemoryMessage message) {
        if (message == null) return null;
        return new MemoryMessageView(
                message.getSessionId(),
                message.getUserId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                message.safeMetadata());
    }

    public List<MemoryMessageView> views(List<OpsMemoryMessage> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        return messages.stream().filter(message -> message != null).map(this::view).toList();
    }

    public OpsMemoryMessage message(MemoryMessageView view) {
        if (view == null) return null;
        return OpsMemoryMessage.builder()
                .sessionId(view.sessionId())
                .userId(view.userId())
                .role(view.role())
                .content(view.content())
                .createdAt(view.createdAt())
                .metadata(view.metadata())
                .build();
    }

    public List<OpsMemoryMessage> messages(List<MemoryMessageView> views) {
        if (views == null || views.isEmpty()) return List.of();
        return views.stream().filter(view -> view != null).map(this::message).toList();
    }

    public ContextMemoryView context(Map<String, Object> memory) {
        if (memory == null || memory.isEmpty()) return null;
        return new ContextMemoryView(
                value(memory.get("memoryId")),
                value(memory.get("memoryType")),
                value(memory.get("scopeType")),
                value(memory.get("scopeId")),
                value(memory.get("title")),
                value(memory.get("summary")),
                value(memory.get("content")),
                value(memory.get("sourceMessageHash")),
                memory);
    }

    public List<ContextMemoryView> contexts(List<Map<String, Object>> memories) {
        if (memories == null || memories.isEmpty()) return List.of();
        return memories.stream().filter(memory -> memory != null).map(this::context).toList();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
