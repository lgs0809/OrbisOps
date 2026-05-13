package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryItem;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;

import java.util.List;

/** Anti-corruption mapper between Trigger memory models and Domain cold-store snapshots. */
public class OpsColdMemoryMapper {

    public ColdMemoryMessageSnapshot snapshot(OpsMemoryMessage message) {
        if (message == null) return null;
        return new ColdMemoryMessageSnapshot(
                message.getSessionId(),
                message.getUserId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                message.safeMetadata());
    }

    public List<ColdMemoryMessageSnapshot> messageSnapshots(List<OpsMemoryMessage> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        return messages.stream()
                .filter(message -> message != null)
                .map(this::snapshot)
                .toList();
    }

    public List<OpsMemoryMessage> messageViews(List<ColdMemoryMessageSnapshot> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        return messages.stream()
                .filter(message -> message != null)
                .map(this::messageView)
                .toList();
    }

    public OpsMemoryMessage messageView(ColdMemoryMessageSnapshot message) {
        if (message == null) return null;
        return OpsMemoryMessage.builder()
                .sessionId(message.sessionId())
                .userId(message.userId())
                .role(message.role())
                .content(message.content())
                .createdAt(message.createdAt())
                .metadata(message.metadata())
                .build();
    }

    public List<ColdMemoryItemSnapshot> snapshots(List<OpsMemoryItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        return items.stream()
                .filter(item -> item != null)
                .map(this::snapshot)
                .toList();
    }

    public ColdMemoryItemSnapshot snapshot(OpsMemoryItem item) {
        if (item == null) return null;
        return new ColdMemoryItemSnapshot(
                item.getSessionId(),
                item.getUserId(),
                item.getMemoryType(),
                item.getContent(),
                item.getImportance(),
                item.getTagsJson(),
                item.getSourceMessageRole(),
                item.getSourceMessageHash(),
                item.getMetadata(),
                item.getCreatedAt());
    }

    public List<OpsMemoryItem> views(List<ColdMemoryItemSnapshot> items) {
        if (items == null || items.isEmpty()) return List.of();
        return items.stream().map(this::view).toList();
    }

    public OpsMemoryItem view(ColdMemoryItemSnapshot item) {
        if (item == null) return null;
        return OpsMemoryItem.builder()
                .sessionId(item.sessionId())
                .userId(item.userId())
                .memoryType(item.memoryType())
                .content(item.content())
                .importance(item.importance())
                .tagsJson(item.tagsJson())
                .sourceMessageRole(item.sourceMessageRole())
                .sourceMessageHash(item.sourceMessageHash())
                .metadata(item.metadata())
                .createdAt(item.createdAt())
                .build();
    }

    public List<OpsMemoryItem> candidateViews(List<MemoryItemCandidate> items) {
        if (items == null || items.isEmpty()) return List.of();
        return items.stream()
                .filter(item -> item != null)
                .map(this::candidateView)
                .toList();
    }

    public OpsMemoryItem candidateView(MemoryItemCandidate item) {
        if (item == null) return null;
        return OpsMemoryItem.builder()
                .sessionId(item.sessionId())
                .userId(item.userId())
                .memoryType(item.memoryType())
                .content(item.content())
                .importance(item.importance())
                .tagsJson(item.tagsJson())
                .sourceMessageRole(item.sourceMessageRole())
                .sourceMessageHash(item.sourceMessageHash())
                .metadata(item.metadata())
                .createdAt(item.createdAt())
                .build();
    }
}
