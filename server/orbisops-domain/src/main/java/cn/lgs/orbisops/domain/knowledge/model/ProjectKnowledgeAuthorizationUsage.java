package cn.lgs.orbisops.domain.knowledge.model;

public record ProjectKnowledgeAuthorizationUsage(
        String projectId,
        String projectName,
        String globalKbId,
        KnowledgeStatus status,
        String enabledBy,
        String enabledAt,
        String updatedAt
) {

    public ProjectKnowledgeAuthorizationUsage {
        projectId = required(projectId, "KNOWLEDGE_PROJECT_ID_REQUIRED");
        projectName = value(projectName);
        if (projectName.isBlank()) projectName = projectId;
        globalKbId = required(globalKbId, "KNOWLEDGE_BASE_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("KNOWLEDGE_STATUS_REQUIRED");
        enabledBy = value(enabledBy);
        enabledAt = value(enabledAt);
        updatedAt = value(updatedAt);
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
