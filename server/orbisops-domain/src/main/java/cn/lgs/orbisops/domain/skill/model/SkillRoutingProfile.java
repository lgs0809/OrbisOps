package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Lightweight retrieval metadata used before any Skill body is loaded. */
public record SkillRoutingProfile(String category,
                                  String subcategory,
                                  String searchDescription,
                                  List<String> useCases,
                                  List<String> exclusions,
                                  List<String> keywords) {

    public SkillRoutingProfile {
        category = text(category, "GENERAL");
        subcategory = text(subcategory, "");
        searchDescription = text(searchDescription, "");
        useCases = values(useCases);
        exclusions = values(exclusions);
        keywords = values(keywords);
    }

    public String positiveText() {
        return String.join(" ",
                category,
                subcategory,
                searchDescription,
                String.join(" ", useCases),
                String.join(" ", keywords)).trim();
    }

    public String negativeText() {
        return String.join(" ", exclusions).trim();
    }

    public static SkillRoutingProfile empty(String description) {
        return new SkillRoutingProfile("GENERAL", "", description, List.of(), List.of(), List.of());
    }

    private static List<String> values(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream()
                .map(value -> text(value, ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(32)
                .toList();
    }

    private static String text(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
