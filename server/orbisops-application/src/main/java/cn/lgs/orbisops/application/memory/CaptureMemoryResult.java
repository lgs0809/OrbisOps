package cn.lgs.orbisops.application.memory;

/** Typed outcome of an explicit Memory write. Learning is handled out of band. */
public record CaptureMemoryResult(GovernedMemoryCreationResult memory) {

    public CaptureMemoryResult {
        if (memory == null) throw new IllegalArgumentException("CAPTURED_MEMORY_RESULT_REQUIRED");
    }
}
