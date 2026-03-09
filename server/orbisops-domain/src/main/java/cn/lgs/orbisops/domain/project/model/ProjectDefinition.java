package cn.lgs.orbisops.domain.project.model;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectDefinition(
        String projectId,
        String name,
        String description,
        String owner,
        List<String> environments,
        String knowledgeBaseId,
        String defaultAgentId,
        List<String> skillIds,
        List<String> sharedMcpIds,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ProjectDefinition {
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        name = required(name, "PROJECT_NAME_REQUIRED");
        description = value(description);
        owner = value(owner);
        environments = values(environments);
        knowledgeBaseId = value(knowledgeBaseId);
        defaultAgentId = value(defaultAgentId);
        skillIds = values(skillIds);
        sharedMcpIds = values(sharedMcpIds);
    }

    public ProjectDefinition update(String name,
                                    String description,
                                    String owner,
                                    List<String> environments,
                                    String knowledgeBaseId,
                                    String defaultAgentId,
                                    List<String> skillIds,
                                    List<String> sharedMcpIds,
                                    LocalDateTime updatedAt) {
        return new ProjectDefinition(
                projectId,
                name,
                description,
                owner,
                environments,
                knowledgeBaseId,
                defaultAgentId,
                skillIds,
                sharedMcpIds,
                enabled,
                createdAt,
                updatedAt);
    }

    private static List<String> values(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(ProjectDefinition::value)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
