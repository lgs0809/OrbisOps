package cn.lgs.orbisops.domain.knowledge.model;

public record KnowledgeRetrievalPolicyKey(
        KnowledgeScope scope,
        String projectId,
        String kbId
) {

    public KnowledgeRetrievalPolicyKey {
        if (scope == null) throw new IllegalArgumentException("KNOWLEDGE_SCOPE_REQUIRED");
        projectId = value(projectId);
        kbId = value(kbId);
        if (kbId.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_BASE_ID_REQUIRED");
        if (scope == KnowledgeScope.PROJECT && projectId.isBlank()) {
            throw new IllegalArgumentException("KNOWLEDGE_PROJECT_ID_REQUIRED");
        }
        if (scope == KnowledgeScope.GLOBAL) {
            projectId = "";
        }
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
