package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.util.Map;
import java.util.Objects;

/** Source identity excludes display timestamps and mutable retrieval metadata. */
public final class MemoryProcessingSourcePolicy {
    private MemoryProcessingSourcePolicy() {}
    public static boolean active(ColdMemoryMessageSnapshot message) {
        return new SemanticMemoryPolicy(new MemoryContentHashPolicy()).active(message.metadata());
    }
    public static String hash(ColdMemoryMessageSnapshot message) {
        return CanonicalObjectHasher.sha256(Map.of("session",text(message.sessionId()),"project",text(message.metadata().get("projectId")),
                "author",text(message.userId()),"sequence",text(message.metadata().get("messageSeq")),
                "role",text(message.role()),"content",text(message.content()),
                "memoryStatus",text(message.metadata().getOrDefault("memory_status","ACTIVE")),
                "supersededBy",text(message.metadata().get("superseded_by"))));
    }
    private static String text(Object value) {return Objects.toString(value,"");}
}
