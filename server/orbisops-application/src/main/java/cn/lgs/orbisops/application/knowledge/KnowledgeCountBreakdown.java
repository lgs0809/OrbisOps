package cn.lgs.orbisops.application.knowledge;

public record KnowledgeCountBreakdown(
        String key,
        long count
) {

    public KnowledgeCountBreakdown {
        key = key == null || key.trim().isBlank() ? "unknown" : key.trim();
        count = Math.max(0L, count);
    }
}
