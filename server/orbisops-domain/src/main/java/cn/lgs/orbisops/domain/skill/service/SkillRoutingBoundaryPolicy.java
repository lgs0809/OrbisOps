package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared lexical boundary semantics for Skill retrieval and deterministic evals. */
public final class SkillRoutingBoundaryPolicy {

    private static final Set<String> OVER_BROAD = Set.of(
            "任何问题",
            "所有问题",
            "所有任务",
            "任何任务",
            "everything",
            "any task",
            "all tasks",
            "always");

    public MatchDecision match(
            String query,
            SkillRoutingProfile profile) {
        return match(query, profile, true);
    }

    /** Keywords retrieve a topic; they do not establish that the task meets a method's use cases. */
    public MatchDecision matchApplicability(String query, SkillRoutingProfile profile) {
        return match(query, profile, false);
    }

    private MatchDecision match(String query, SkillRoutingProfile profile, boolean includeKeywords) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank() || profile == null) {
            return new MatchDecision(false, 0D, 0D, "EMPTY_QUERY_OR_PROFILE");
        }
        double positive = maximumSimilarity(
                normalizedQuery,
                includeKeywords ? merge(profile.useCases(), profile.keywords()) : profile.useCases());
        double negative = maximumSimilarity(
                normalizedQuery,
                profile.exclusions());
        boolean blocked = negative >= 0.45D && negative >= positive;
        boolean matched = positive >= 0.35D && !blocked;
        return new MatchDecision(
                matched,
                rounded(positive),
                rounded(negative),
                blocked ? "WHEN_NOT_TO_USE_MATCHED"
                        : matched ? "WHEN_TO_USE_MATCHED"
                        : "NO_POSITIVE_MATCH");
    }

    public List<String> validate(SkillRoutingProfile profile) {
        if (profile == null) return List.of("SKILL_ROUTING_PROFILE_REQUIRED");
        Set<String> failures = new java.util.LinkedHashSet<>();
        for (String useCase : profile.useCases()) {
            if (overBroad(useCase)) {
                failures.add("SKILL_ROUTING_WHEN_TO_USE_TOO_BROAD");
            }
            for (String exclusion : profile.exclusions()) {
                if (similarity(
                        boundarySubject(useCase),
                        boundarySubject(exclusion)) >= 0.82D) {
                    failures.add("SKILL_ROUTING_BOUNDARY_OVERLAP");
                }
            }
        }
        return List.copyOf(failures);
    }

    private double maximumSimilarity(
            String query,
            List<String> candidates) {
        double best = 0D;
        for (String candidate : candidates) {
            best = Math.max(best, similarity(query, candidate));
        }
        return best;
    }

    private double similarity(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return 0D;
        if (normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft)) {
            double lengthRatio = (double) Math.min(
                    normalizedLeft.length(),
                    normalizedRight.length())
                    / Math.max(normalizedLeft.length(), normalizedRight.length());
            return Math.max(0.72D, lengthRatio);
        }
        Set<String> leftGrams = grams(normalizedLeft);
        Set<String> rightGrams = grams(normalizedRight);
        if (leftGrams.isEmpty() || rightGrams.isEmpty()) return 0D;
        Set<String> intersection = new HashSet<>(leftGrams);
        intersection.retainAll(rightGrams);
        return 2D * intersection.size()
                / (leftGrams.size() + rightGrams.size());
    }

    private Set<String> grams(String value) {
        String normalized = normalize(value).replace(" ", "");
        Set<String> grams = new HashSet<>();
        if (normalized.length() == 1) grams.add(normalized);
        for (int index = 0; index < normalized.length() - 1; index++) {
            grams.add(normalized.substring(index, index + 2));
        }
        return grams;
    }

    private List<String> merge(
            List<String> left,
            List<String> right) {
        return java.util.stream.Stream.concat(
                        left == null ? java.util.stream.Stream.empty() : left.stream(),
                        right == null ? java.util.stream.Stream.empty() : right.stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private boolean overBroad(String value) {
        String normalized = normalize(value);
        return OVER_BROAD.stream().anyMatch(normalized::contains);
    }

    private String boundarySubject(String value) {
        return normalize(value)
                .replaceFirst(
                        "^(不要用于|不能用于|禁止用于|不适用|不支持|"
                                + "when not to use|not for|unsupported)\\s*",
                        "")
                .trim();
    }

    private String normalize(String value) {
        return value == null ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[\\p{Punct}\\p{IsPunctuation}]+", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private double rounded(double value) {
        return Math.round(value * 10_000D) / 10_000D;
    }

    public record MatchDecision(
            boolean matched,
            double positiveScore,
            double negativeScore,
            String reasonCode) {
    }
}
