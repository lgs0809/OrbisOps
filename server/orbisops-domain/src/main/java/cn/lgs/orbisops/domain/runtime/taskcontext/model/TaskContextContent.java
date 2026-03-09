package cn.lgs.orbisops.domain.runtime.taskcontext.model;

import java.util.List;

public record TaskContextContent(
        String goal,
        List<String> temporaryConstraints,
        List<String> knownFacts,
        List<String> ruledOut,
        List<String> openQuestions,
        List<String> completedActions,
        List<String> pendingActions,
        List<String> lastToolResultsSummary,
        List<String> usedSkills) {

    public TaskContextContent {
        goal = text(goal);
        temporaryConstraints = values(temporaryConstraints);
        knownFacts = values(knownFacts);
        ruledOut = values(ruledOut);
        openQuestions = values(openQuestions);
        completedActions = values(completedActions);
        pendingActions = values(pendingActions);
        lastToolResultsSummary = values(lastToolResultsSummary);
        usedSkills = values(usedSkills);
    }

    public static TaskContextContent empty() {
        return new TaskContextContent("", List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> values(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream()
                .map(TaskContextContent::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
