package cn.lgs.orbisops.application.memory;

/** Outcome of one semantic-memory write attempt. */
public record SemanticMemoryWriteResult(
        boolean written,
        String storage) {

    public SemanticMemoryWriteResult {
        storage = storage == null ? "" : storage;
    }

    public static SemanticMemoryWriteResult vector() {
        return new SemanticMemoryWriteResult(true, "vector");
    }

    public static SemanticMemoryWriteResult lexical() {
        return new SemanticMemoryWriteResult(true, "lexical");
    }

    public static SemanticMemoryWriteResult skipped() {
        return new SemanticMemoryWriteResult(false, "");
    }
}
