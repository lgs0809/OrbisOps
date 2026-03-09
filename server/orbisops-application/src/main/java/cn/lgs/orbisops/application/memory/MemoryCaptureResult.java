package cn.lgs.orbisops.application.memory;

/** Capture outcome returned to compatibility facades and tests. */
public record MemoryCaptureResult(
        boolean captured,
        MemoryMessageView message) {

    public static MemoryCaptureResult captured(MemoryMessageView message) {
        return new MemoryCaptureResult(true, message);
    }

    public static MemoryCaptureResult skipped() {
        return new MemoryCaptureResult(false, null);
    }
}
