package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;

import java.time.Clock;
import java.util.List;

/** Application service for assembling authoritative references to selected Context Memory entries. */
public class MemorySelectionReferenceApplicationService {

    private final MemoryContentHashPolicy contentHashPolicy;
    private final Clock clock;

    public MemorySelectionReferenceApplicationService(MemoryContentHashPolicy contentHashPolicy,
                                                      Clock clock) {
        this.contentHashPolicy = contentHashPolicy == null
                ? new MemoryContentHashPolicy()
                : contentHashPolicy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public List<MemorySelectionReference> assemble(List<ContextMemoryView> memories) {
        if (memories == null || memories.isEmpty()) return List.of();
        return memories.stream()
                .filter(memory -> memory != null)
                .map(this::reference)
                .toList();
    }

    private MemorySelectionReference reference(ContextMemoryView memory) {
        String content = hasText(memory.content()) ? memory.content() : value(memory.summary());
        String contentHash = contentHashPolicy.stableHash(content);
        return new MemorySelectionReference(
                value(memory.memoryId()),
                1,
                contentHash,
                value(memory.memoryType()),
                value(memory.scopeType()),
                value(memory.scopeId()),
                value(memory.sourceMessageHash()),
                contentHash,
                clock.instant().toString(),
                false);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
