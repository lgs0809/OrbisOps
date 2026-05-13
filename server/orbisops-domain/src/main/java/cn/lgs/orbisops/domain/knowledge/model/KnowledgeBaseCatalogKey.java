package cn.lgs.orbisops.domain.knowledge.model;

public record KnowledgeBaseCatalogKey(
        KnowledgeScope scope,
        String projectId,
        String kbId
) {

    public KnowledgeBaseCatalogKey {
        if (scope == null) throw new IllegalArgumentException("KNOWLEDGE_SCOPE_REQUIRED");
        projectId = value(projectId);
        kbId = required(kbId, "KNOWLEDGE_BASE_ID_REQUIRED");
        if (scope == KnowledgeScope.PROJECT && projectId.isBlank()) {
            throw new IllegalArgumentException("KNOWLEDGE_PROJECT_ID_REQUIRED");
        }
        if (scope == KnowledgeScope.GLOBAL) {
            projectId = "";
        }
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
