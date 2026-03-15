package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;

import java.time.LocalDateTime;
import java.util.List;

/** Typed project directory entry exposed by the project access boundary. */
public record ProjectCatalogEntry(
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

    public ProjectCatalogEntry {
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        name = text(name, projectId);
        description = value(description);
        owner = value(owner);
        environments = copy(environments);
        knowledgeBaseId = value(knowledgeBaseId);
        defaultAgentId = value(defaultAgentId);
        skillIds = copy(skillIds);
        sharedMcpIds = copy(sharedMcpIds);
    }

    public static ProjectCatalogEntry from(ProjectDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_REQUIRED");
        }
        return new ProjectCatalogEntry(
                definition.projectId(),
                definition.name(),
                definition.description(),
                definition.owner(),
                definition.environments(),
                definition.knowledgeBaseId(),
                definition.defaultAgentId(),
                definition.skillIds(),
                definition.sharedMcpIds(),
                definition.enabled(),
                definition.createdAt(),
                definition.updatedAt());
    }

    private static List<String> copy(List<String> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private static String required(String input, String reasonCode) {
        String normalized = value(input);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(reasonCode);
        }
        return normalized;
    }

    private static String text(String input, String fallback) {
        String normalized = value(input);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
