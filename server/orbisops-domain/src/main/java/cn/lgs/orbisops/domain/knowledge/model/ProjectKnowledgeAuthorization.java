package cn.lgs.orbisops.domain.knowledge.model;

public record ProjectKnowledgeAuthorization(
        String projectId,
        String globalKbId,
        KnowledgeStatus status,
        String enabledBy,
        String enabledAt,
        String updatedAt
) {

    public ProjectKnowledgeAuthorization {
        projectId = required(projectId, "KNOWLEDGE_PROJECT_ID_REQUIRED");
        globalKbId = required(globalKbId, "KNOWLEDGE_BASE_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("KNOWLEDGE_STATUS_REQUIRED");
        enabledBy = required(enabledBy, "KNOWLEDGE_ENABLED_BY_REQUIRED");
        enabledAt = value(enabledAt);
        updatedAt = value(updatedAt);
    }

    public static ProjectKnowledgeAuthorization enabled(String projectId,
                                                        String globalKbId,
                                                        String enabledBy) {
        return new ProjectKnowledgeAuthorization(
                projectId, globalKbId, KnowledgeStatus.ENABLED, enabledBy, "", "");
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
