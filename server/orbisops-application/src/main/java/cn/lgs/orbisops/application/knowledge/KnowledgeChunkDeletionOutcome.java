package cn.lgs.orbisops.application.knowledge;

public record KnowledgeChunkDeletionOutcome(
        String knowledgeTag,
        String scope,
        String projectId,
        long deletedChunks
) {

    public KnowledgeChunkDeletionOutcome {
        knowledgeTag = value(knowledgeTag);
        scope = value(scope);
        projectId = value(projectId);
        deletedChunks = Math.max(0L, deletedChunks);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
