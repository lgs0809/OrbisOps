package cn.lgs.orbisops.application.memory;

/** Rendered memory context plus bounded-section diagnostics. */
public record MemoryContextRenderingResult(
        String context,
        int contextMemoryCount,
        int itemCount,
        int messageCount,
        boolean truncated) {

    public MemoryContextRenderingResult {
        context = context == null ? "" : context;
    }

    public static MemoryContextRenderingResult empty() {
        return new MemoryContextRenderingResult("", 0, 0, 0, false);
    }
}
