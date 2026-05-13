package cn.lgs.orbisops.application.memory;

import java.util.List;

/** Fully assembled memory context and typed selected references. */
public record MemoryQueryResult(
        String context,
        List<MemorySelectionReference> references) {

    public MemoryQueryResult {
        context = context == null ? "" : context;
        references = references == null ? List.of() : List.copyOf(references);
    }

    public static MemoryQueryResult empty() {
        return new MemoryQueryResult("", List.of());
    }
}
