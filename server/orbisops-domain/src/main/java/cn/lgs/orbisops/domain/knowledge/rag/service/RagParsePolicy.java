package cn.lgs.orbisops.domain.knowledge.rag.service;

/**
 * Structure-aware ingestion policy.
 *
 * maxSegmentChars is only applied after the parser has identified a semantic
 * boundary such as a Markdown section, PDF paragraph, table row group, code
 * symbol or conversation turn. hardSplitOverlapChars is used only when one
 * indivisible structural block still exceeds that limit.
 */
public record RagParsePolicy(String scope,
                             String projectId,
                             int maxSegmentChars,
                             int hardSplitOverlapChars) {

    public static final int DEFAULT_MAX_SEGMENT_CHARS = 3000;
    public static final int DEFAULT_HARD_SPLIT_OVERLAP_CHARS = 0;

    public static RagParsePolicy defaults() {
        return new RagParsePolicy("GLOBAL", "", DEFAULT_MAX_SEGMENT_CHARS, DEFAULT_HARD_SPLIT_OVERLAP_CHARS);
    }

    public RagParsePolicy normalized(int fallbackMaxSegmentChars) {
        int fallback = clamp(fallbackMaxSegmentChars, 1000, 12000);
        int maxChars = clamp(maxSegmentChars <= 0 ? fallback : maxSegmentChars, 1000, 12000);
        int overlapChars = clamp(hardSplitOverlapChars, 0, Math.max(0, maxChars / 2));
        return new RagParsePolicy(
                normalizeScope(scope),
                text(projectId),
                maxChars,
                overlapChars);
    }

    private static String normalizeScope(String value) {
        return "PROJECT".equalsIgnoreCase(value) ? "PROJECT" : "GLOBAL";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
