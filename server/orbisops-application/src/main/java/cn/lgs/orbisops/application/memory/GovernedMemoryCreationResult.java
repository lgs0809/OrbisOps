package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;

/** Typed result of governed-memory creation or idempotent lookup. */
public record GovernedMemoryCreationResult(
        GovernedMemorySnapshot snapshot,
        String conflictId,
        boolean duplicate) {

    public GovernedMemoryCreationResult {
        if (snapshot == null) throw new IllegalArgumentException("GOVERNED_MEMORY_SNAPSHOT_REQUIRED");
        conflictId = conflictId == null ? "" : conflictId.trim();
    }

    public static GovernedMemoryCreationResult duplicate(GovernedMemorySnapshot snapshot) {
        return new GovernedMemoryCreationResult(snapshot, "", true);
    }

    public static GovernedMemoryCreationResult created(
            GovernedMemorySnapshot snapshot,
            String conflictId) {
        return new GovernedMemoryCreationResult(snapshot, conflictId, false);
    }
}
