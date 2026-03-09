package cn.lgs.orbisops.application.memory;

import java.util.List;

/** Outcome of a best-effort multi-store session clear. */
public record MemorySessionClearResult(
        boolean attempted,
        List<String> failedOperations) {

    public MemorySessionClearResult {
        failedOperations = failedOperations == null ? List.of() : List.copyOf(failedOperations);
    }

    public boolean fullyCleared() {
        return attempted && failedOperations.isEmpty();
    }

    public static MemorySessionClearResult skipped() {
        return new MemorySessionClearResult(false, List.of());
    }
}
