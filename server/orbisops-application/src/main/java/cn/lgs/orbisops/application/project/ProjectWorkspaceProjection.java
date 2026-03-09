package cn.lgs.orbisops.application.project;

import java.util.List;

public record ProjectWorkspaceProjection(
        ProjectWorkspaceProjectionRequest.Project project,
        List<ProjectWorkspaceProjectionRequest.SkillReference> projectSkills,
        List<ProjectWorkspaceProjectionRequest.SkillReference> enabledGlobalSkills,
        List<String> knowledgeBaseIds,
        List<ProjectWorkspaceProjectionRequest.KnowledgeBaseReference> projectKnowledgeBases,
        List<ProjectWorkspaceProjectionRequest.KnowledgeBaseReference> enabledGlobalKnowledgeBases,
        int dataResourceCount,
        int sourceRepositoryCount,
        int executionResourceCount,
        int resourceCount,
        int generatedMcpCount,
        boolean defaultAgentPublished,
        boolean readyForInvestigation,
        String readinessReason,
        List<DiagnosticScenario> diagnosticScenarios,
        String recommendedScenarioId,
        List<OnboardingStep> onboarding
) {

    public ProjectWorkspaceProjection {
        if (project == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECT_REQUIRED");
        }
        projectSkills = copy(projectSkills);
        enabledGlobalSkills = copy(enabledGlobalSkills);
        knowledgeBaseIds = copy(knowledgeBaseIds);
        projectKnowledgeBases = copy(projectKnowledgeBases);
        enabledGlobalKnowledgeBases = copy(enabledGlobalKnowledgeBases);
        dataResourceCount = Math.max(dataResourceCount, 0);
        sourceRepositoryCount = Math.max(sourceRepositoryCount, 0);
        executionResourceCount = Math.max(executionResourceCount, 0);
        resourceCount = Math.max(resourceCount, 0);
        generatedMcpCount = Math.max(generatedMcpCount, 0);
        readinessReason = text(readinessReason);
        diagnosticScenarios = copy(diagnosticScenarios);
        recommendedScenarioId = text(recommendedScenarioId);
        onboarding = copy(onboarding);
    }

    public record DiagnosticScenario(
            String scenarioId,
            String name,
            String description,
            String promptTemplate,
            boolean ready,
            String unavailableReason,
            List<String> allowedSources
    ) {
        public DiagnosticScenario {
            scenarioId = text(scenarioId);
            name = text(name);
            description = text(description);
            promptTemplate = text(promptTemplate);
            unavailableReason = ready ? "" : text(unavailableReason);
            allowedSources = copy(allowedSources);
        }
    }

    public record OnboardingStep(
            String key,
            String label,
            boolean completed,
            boolean optional
    ) {
        public OnboardingStep {
            key = text(key);
            label = text(label);
        }
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null || source.isEmpty() ? List.of() : List.copyOf(source);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
