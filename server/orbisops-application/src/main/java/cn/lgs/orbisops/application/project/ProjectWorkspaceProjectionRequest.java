package cn.lgs.orbisops.application.project;

import java.util.List;

public record ProjectWorkspaceProjectionRequest(
        Project project,
        List<SkillReference> projectSkills,
        List<SkillReference> enabledGlobalSkills,
        List<String> knowledgeBaseIds,
        List<KnowledgeBaseReference> projectKnowledgeBases,
        List<KnowledgeBaseReference> enabledGlobalKnowledgeBases,
        List<EvidenceSource> evidenceSources,
        int dataResourceCount,
        int generatedMcpCount
) {

    public ProjectWorkspaceProjectionRequest {
        if (project == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECT_REQUIRED");
        }
        projectSkills = copy(projectSkills);
        enabledGlobalSkills = copy(enabledGlobalSkills);
        knowledgeBaseIds = values(knowledgeBaseIds);
        projectKnowledgeBases = copy(projectKnowledgeBases);
        enabledGlobalKnowledgeBases = copy(enabledGlobalKnowledgeBases);
        evidenceSources = copy(evidenceSources);
        dataResourceCount = Math.max(dataResourceCount, 0);
        generatedMcpCount = Math.max(generatedMcpCount, 0);
    }

    public record Project(
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
            String createdAt,
            String updatedAt
    ) {
        public Project {
            projectId = text(projectId);
            name = text(name);
            description = text(description);
            owner = text(owner);
            environments = values(environments);
            knowledgeBaseId = text(knowledgeBaseId);
            defaultAgentId = text(defaultAgentId);
            skillIds = values(skillIds);
            sharedMcpIds = values(sharedMcpIds);
            createdAt = text(createdAt);
            updatedAt = text(updatedAt);
        }
    }

    public record SkillReference(
            String skillId,
            String name,
            String skillName,
            String scope
    ) {
        public SkillReference {
            skillId = text(skillId);
            name = fallback(name, skillId);
            skillName = fallback(skillName, name);
            scope = text(scope);
        }
    }

    public record KnowledgeBaseReference(
            String kbId,
            String knowledgeTag,
            String name,
            String kbName,
            String projectId,
            String scope,
            String description,
            String status,
            long documentCount,
            long chunkCount,
            String sourceType,
            String createTime,
            String updateTime
    ) {
        public KnowledgeBaseReference {
            kbId = text(kbId);
            knowledgeTag = fallback(knowledgeTag, kbId);
            name = text(name);
            kbName = fallback(kbName, name);
            projectId = text(projectId);
            scope = text(scope);
            description = text(description);
            status = text(status);
            documentCount = Math.max(documentCount, 0L);
            chunkCount = Math.max(chunkCount, 0L);
            sourceType = text(sourceType);
            createTime = text(createTime);
            updateTime = text(updateTime);
        }
    }

    public record EvidenceSource(String type, String status) {
        public EvidenceSource {
            type = text(type);
            status = text(status);
        }
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null || source.isEmpty() ? List.of() : List.copyOf(source);
    }

    private static List<String> values(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(ProjectWorkspaceProjectionRequest::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String fallback(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? text(fallback) : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
