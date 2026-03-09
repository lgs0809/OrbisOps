package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelection;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelection.RankedSkill;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelection.SuppressedSkill;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelectionRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SkillRuntimeSelectionPolicy {

    private final SkillRoutingProfilePolicy routingProfilePolicy = new SkillRoutingProfilePolicy();

    public SkillRuntimeSelection select(SkillRuntimeSelectionRequest request,
                                        List<SkillRuntimeCandidate> candidates,
                                        Map<String, Double> semanticScores) {
        if (request.requestedSkillIds().size() > request.maxExplicitSkills()) {
            throw new IllegalArgumentException("TOO_MANY_EXPLICIT_SKILLS: 最多显式选择 "
                    + request.maxExplicitSkills() + " 个 Skill");
        }
        List<SkillRuntimeCandidate> active = candidates == null ? List.of() : candidates.stream()
                .filter(SkillRuntimeCandidate::activeAtUse)
                .toList();
        Set<String> available = active.stream().map(SkillRuntimeCandidate::skillId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> unavailable = new LinkedHashSet<>(request.requestedSkillIds());
        unavailable.removeAll(available);
        if (!unavailable.isEmpty()) {
            throw new IllegalStateException("REQUESTED_SKILL_NOT_ACTIVE: " + String.join(",", unavailable));
        }

        Map<String, Double> safeSemanticScores = semanticScores == null ? Map.of() : semanticScores;
        List<RankedSkill> ranked = active.stream()
                .map(candidate -> rank(request, candidate, safeSemanticScores))
                .sorted(Comparator.comparing(RankedSkill::explicit).reversed()
                        .thenComparing(RankedSkill::score, Comparator.reverseOrder())
                        .thenComparing(item -> item.candidate().skillId()))
                .toList();
        int catalogLimit = Math.max(request.catalogCandidateLimit(), request.requestedSkillIds().size());
        List<RankedSkill> catalog = ranked.stream().limit(catalogLimit).toList();
        int selectedLimit = Math.max(request.selectedLimit(), request.requestedSkillIds().size());
        List<RankedSkill> selected = new ArrayList<>();
        List<String> selectedDescriptors = new ArrayList<>();
        List<SuppressedSkill> suppressed = new ArrayList<>();
        for (RankedSkill item : ranked) {
            if (selected.size() >= selectedLimit) break;
            if (!item.explicit() && item.score() < request.minRelevanceScore()) continue;
            String descriptor = item.candidate().retrievalDescriptor();
            if (item.explicit()) {
                selected.add(item);
                selectedDescriptors.add(descriptor);
                continue;
            }
            int similarIndex = similarIndex(selectedDescriptors, descriptor, request.similarSuppressionThreshold());
            if (similarIndex >= 0) {
                suppressed.add(new SuppressedSkill(item, selected.get(similarIndex).candidate().skillId(),
                        "SIMILAR_SKILL_SUPPRESSED"));
                continue;
            }
            selected.add(item);
            selectedDescriptors.add(descriptor);
        }
        return new SkillRuntimeSelection(catalog, selected, suppressed, active.size());
    }

    public List<RankedSkill> rankFrozenCatalog(List<SkillRuntimeCandidate> candidates, String query, int limit) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream()
                .map(candidate -> new RankedSkill(
                        candidate,
                        roundScore(textScore(query, candidate.retrievalDescriptor())),
                        false))
                .sorted(Comparator.comparing(RankedSkill::score, Comparator.reverseOrder())
                        .thenComparing(item -> item.candidate().skillId()))
                .limit(Math.max(1, Math.min(20, limit)))
                .toList();
    }

    private RankedSkill rank(SkillRuntimeSelectionRequest request,
                             SkillRuntimeCandidate candidate,
                             Map<String, Double> semanticScores) {
        boolean explicit = request.requestedSkillIds().contains(candidate.skillId());
        if (explicit) return new RankedSkill(candidate, 100D, true);
        double lexical = textScore(request.query(), candidate.retrievalDescriptor());
        if ("PROJECT".equalsIgnoreCase(candidate.scope())
                && request.projectId().equals(candidate.projectId())) lexical += 0.03D;
        if (!request.agentId().isBlank()
                && candidate.descriptor().toLowerCase(Locale.ROOT)
                .contains(request.agentId().toLowerCase(Locale.ROOT))) lexical += 0.08D;
        lexical = Math.min(1D, lexical);
        Double semantic = semanticScores.get(candidate.skillId());
        double score = semantic == null ? lexical
                : lexical * (1D - request.semanticWeight()) + semantic * request.semanticWeight();
        String queryCategory = routingProfilePolicy.category(request.query());
        if (!"GENERAL".equals(queryCategory)
                && queryCategory.equalsIgnoreCase(candidate.routingProfile().category())) {
            score += request.categoryBoost();
        }
        double exclusionScore = textScore(request.query(), candidate.exclusionDescriptor());
        score -= exclusionScore * request.negativePenaltyWeight();
        score = Math.max(0D, Math.min(1D, score));
        return new RankedSkill(candidate, roundScore(score), false);
    }

    private int similarIndex(List<String> selectedDescriptors, String descriptor, double threshold) {
        for (int index = 0; index < selectedDescriptors.size(); index++) {
            if (dice(normalize(selectedDescriptors.get(index)), normalize(descriptor)) >= threshold) return index;
        }
        return -1;
    }

    private double textScore(String query, String descriptor) {
        String left = normalize(query);
        String right = normalize(descriptor);
        if (left.isBlank() || right.isBlank()) return 0D;
        double score = dice(left, right) * 0.72D;
        for (String token : tokens(left)) {
            if (token.length() >= 2 && right.contains(token)) score += 0.08D;
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

    private double roundScore(double value) {
        return Math.round(value * 10_000D) / 10_000D;
    }
}
