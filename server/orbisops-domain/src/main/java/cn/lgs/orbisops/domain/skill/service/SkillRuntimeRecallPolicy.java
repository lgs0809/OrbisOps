package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded first-stage recall over the complete lightweight Skill catalog. */
public final class SkillRuntimeRecallPolicy {

    private final SkillRoutingProfilePolicy routingProfilePolicy = new SkillRoutingProfilePolicy();

    public List<SkillRuntimeCandidate> recall(String query,
                                              List<SkillRuntimeCandidate> candidates,
                                              Set<String> requestedSkillIds,
                                              int limit) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Set<String> explicit = requestedSkillIds == null ? Set.of() : requestedSkillIds;
        int boundedLimit = Math.max(explicit.size(), Math.max(1, Math.min(256, limit)));
        String queryCategory = routingProfilePolicy.category(query);
        List<RecallCandidate> ranked = candidates.stream()
                .filter(SkillRuntimeCandidate::activeAtUse)
                .map(candidate -> new RecallCandidate(
                        candidate,
                        explicit.contains(candidate.skillId()),
                        recallScore(query, queryCategory, candidate)))
                .sorted(Comparator.comparing(RecallCandidate::explicit).reversed()
                        .thenComparing(RecallCandidate::score, Comparator.reverseOrder())
                        .thenComparing(item -> item.candidate().skillId()))
                .toList();
        List<SkillRuntimeCandidate> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RecallCandidate item : ranked) {
            if (result.size() >= boundedLimit) break;
            if (seen.add(item.candidate().skillId())) result.add(item.candidate());
        }
        return List.copyOf(result);
    }

    private double recallScore(String query,
                               String queryCategory,
                               SkillRuntimeCandidate candidate) {
        double score = textScore(query, candidate.retrievalDescriptor());
        if (!"GENERAL".equals(queryCategory)
                && queryCategory.equalsIgnoreCase(candidate.routingProfile().category())) {
            score += 0.3D;
        }
        return Math.min(1D, score);
    }

    private double textScore(String query, String descriptor) {
        String left = normalize(query);
        String right = normalize(descriptor);
        if (left.isBlank() || right.isBlank()) return 0D;
        double score = dice(left, right) * 0.68D;
        for (String token : tokens(left)) {
            if (token.length() >= 2 && right.contains(token)) score += 0.1D;
        }
        return Math.min(1D, score);
    }

    private Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : normalize(value).split("[^\\p{L}\\p{N}_-]+")) {
            if (!token.isBlank()) result.add(token);
        }
        return result;
    }

    private double dice(String left, String right) {
        Set<String> leftGrams = bigrams(left);
        Set<String> rightGrams = bigrams(right);
        if (leftGrams.isEmpty() || rightGrams.isEmpty()) return 0D;
        Set<String> intersection = new LinkedHashSet<>(leftGrams);
        intersection.retainAll(rightGrams);
        return 2D * intersection.size() / (leftGrams.size() + rightGrams.size());
    }

    private Set<String> bigrams(String value) {
        String normalized = normalize(value).replace(" ", "");
        Set<String> grams = new LinkedHashSet<>();
        if (normalized.length() == 1) grams.add(normalized);
        for (int index = 0; index < normalized.length() - 1; index++) {
            grams.add(normalized.substring(index, index + 2));
        }
        return grams;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private record RecallCandidate(SkillRuntimeCandidate candidate,
                                   boolean explicit,
                                   double score) {
    }
}
