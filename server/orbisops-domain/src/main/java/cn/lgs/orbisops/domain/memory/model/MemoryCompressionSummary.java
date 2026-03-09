package cn.lgs.orbisops.domain.memory.model;

/** Summary content and provenance selected for context compression. */
public record MemoryCompressionSummary(
        String content,
        String source) {

    public MemoryCompressionSummary {
        content = value(content);
        source = value(source);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
