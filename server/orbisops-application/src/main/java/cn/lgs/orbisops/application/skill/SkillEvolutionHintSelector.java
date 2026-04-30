package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic relevance, evidence, diversity, hard-case and MMR hint selection. */
public final class SkillEvolutionHintSelector {

    private static final int RELEVANT_HINT_LIMIT = 8;
    private static final int HARD_CASE_QUOTA = 2;
    private static final double HINT_SIMILARITY_THRESHOLD = 0.16D;
    private static final double MMR_DUPLICATION_PENALTY = 0.28D;
    private static final double SOURCE_DIVERSITY_BONUS = 0.08D;

    private final SkillEvolutionPayloadCodec payloadCodecPort;

    public SkillEvolutionHintSelector(
            SkillEvolutionPayloadCodec payloadCodecPort) {
        if (payloadCodecPort == null) {
            throw new IllegalArgumentException(
                    "SKILL_EVOLUTION_PAYLOAD_CODEC_REQUIRED");
        }
        this.payloadCodecPort = payloadCodecPort;
    }

    List<SkillEvolutionSelectedHint> select(
            List<SkillEvolutionHintSnapshot> hints,
            String currentContext,
            String currentSignalId,
            String currentRunId,
            String signalType) {
        List<SkillEvolutionHintSnapshot> safe = safeHints(hints);
        Instant newest = safe.stream()
                .map(SkillEvolutionHintSnapshot::createdAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
        List<ScoredHint> candidates = safe.stream()
                .map(hint -> score(hint, currentContext, currentSignalId,
                        currentRunId, signalType, newest))
                .filter(ScoredHint::eligible)
                .sorted(baseOrder())
                .toList();
        if (candidates.isEmpty()) return List.of();

        List<ScoredHint> selected = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();

        candidates.stream().filter(ScoredHint::current)
                .findFirst().ifPresent(item -> add(selected, selectedIds, item));
        candidates.stream().filter(ScoredHint::hardCase)
                .limit(HARD_CASE_QUOTA)
                .forEach(item -> add(selected, selectedIds, item));

        while (selected.size() < RELEVANT_HINT_LIMIT) {
            ScoredHint best = candidates.stream()
                    .filter(item -> !selectedIds.contains(item.hint().hintId()))
                    .filter(item -> novel(item, selected))
                    .max(mmrOrder(selected))
                    .orElse(null);
            if (best == null) break;
            add(selected, selectedIds, best);
        }
        return selected.stream()
                .limit(RELEVANT_HINT_LIMIT)
                .map(item -> new SkillEvolutionSelectedHint(item.hint(), item.content()))
                .toList();
    }

    private ScoredHint score(
            SkillEvolutionHintSnapshot hint,
            String context,
            String currentSignalId,
            String currentRunId,
            String signalType,
            Instant newest) {
        Map<String, Object> payload = payloadCodecPort.decodeObject(hint.contentJson());
        String content = hintText(payload);
        boolean current = text(currentSignalId).equals(hint.signalId());
        boolean sameRun = !text(currentRunId).isBlank()
                && text(currentRunId).equals(hint.runId());
        double relevance = textSimilarity(content, context);
        double evidence = evidenceQuality(payload);
        boolean hardCase = hardCase(payload);
        double recency = recency(hint.createdAt(), newest);
        String sourceKey = firstText(
                payload.get("sourceType"), payload.get("source"),
                hint.hintType(), hint.runId(), hint.hintId());
        boolean eligible = current
                || relevance >= HINT_SIMILARITY_THRESHOLD
                || ("USER_ASSERTED_PROCEDURE".equals(signalType) && sameRun)
                || (hardCase && evidence >= 0.5D);
        double score = relevance * 0.48D
                + evidence * 0.22D
                + recency * 0.10D
                + (hardCase ? 0.12D : 0D)
                + (current ? 0.16D : 0D)
                + (sameRun ? 0.06D : 0D);
        return new ScoredHint(
                hint, content, sourceKey, current, hardCase, eligible,
                relevance, evidence, recency, score);
    }

    private Comparator<ScoredHint> baseOrder() {
        return Comparator.comparingDouble(ScoredHint::baseScore).reversed()
                .thenComparing(Comparator.comparingDouble(ScoredHint::evidenceQuality).reversed())
                .thenComparing(Comparator.comparingDouble(ScoredHint::recency).reversed())
                .thenComparing(item -> item.hint().hintId());
    }

    private Comparator<ScoredHint> mmrOrder(List<ScoredHint> selected) {
        Set<String> selectedSources = selected.stream()
                .map(ScoredHint::sourceKey).collect(java.util.stream.Collectors.toSet());
        return Comparator.comparingDouble((ScoredHint item) ->
                        mmrScore(item, selected, selectedSources))
                .thenComparingDouble(ScoredHint::baseScore)
                .thenComparing(item -> item.hint().hintId(), Comparator.reverseOrder());
    }

    private double mmrScore(
            ScoredHint candidate,
            List<ScoredHint> selected,
            Set<String> selectedSources) {
        double duplication = selected.stream()
                .mapToDouble(item -> textSimilarity(candidate.content(), item.content()))
                .max().orElse(0D);
        double diversity = selectedSources.contains(candidate.sourceKey())
                ? 0D : SOURCE_DIVERSITY_BONUS;
        return candidate.baseScore() + diversity
                - duplication * MMR_DUPLICATION_PENALTY;
    }

    private void add(
            List<ScoredHint> selected,
            Set<String> selectedIds,
            ScoredHint candidate) {
        if (candidate == null || selectedIds.contains(candidate.hint().hintId())) return;
        if (!candidate.current() && !novel(candidate, selected)) return;
        selectedIds.add(candidate.hint().hintId());
        selected.add(candidate);
    }

    private boolean novel(
            ScoredHint candidate,
            List<ScoredHint> selected) {
        return selected.stream()
                .mapToDouble(item -> textSimilarity(candidate.content(), item.content()))
                .max().orElse(0D) < 0.94D;
    }

    private String hintText(Map<String, Object> content) {
        for (String key : List.of(
                "content", "normalizedUserGoal", "finalOutput", "summary")) {
            String value = text(content.get(key));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private double evidenceQuality(Map<String, Object> payload) {
        Object explicit = payload.get("evidenceQuality");
        if (explicit instanceof Number number) return bounded(number.doubleValue());
        int evidenceCount = iterableSize(payload.get("evidenceRefs"))
                + iterableSize(payload.get("supportingTrajectoryIds"))
                + iterableSize(payload.get("counterexampleIds"));
        if (evidenceCount > 0) return Math.min(1D, evidenceCount / 4D);
        return booleanValue(payload.get("trustedEvidence")) ? 0.75D : 0D;
    }

    private boolean hardCase(Map<String, Object> payload) {
        if (booleanValue(payload.get("hardCase"))) return true;
        String type = firstText(payload.get("caseType"), payload.get("difficulty"));
        return type.toUpperCase(Locale.ROOT).contains("HARD")
                || type.toUpperCase(Locale.ROOT).contains("COUNTEREXAMPLE");
    }

    private double recency(Instant createdAt, Instant newest) {
        if (createdAt == null || newest == null || newest.equals(Instant.EPOCH)) return 0D;
        long hours = Math.max(0L, Duration.between(createdAt, newest).toHours());
        return 1D / (1D + hours / 24D);
    }

    private double textSimilarity(String left, String right) {
        Set<String> leftWords = words(left);
        Set<String> rightWords = words(right);
        double characterScore = dice(bigrams(left), bigrams(right));
        if (leftWords.size() > 1 && rightWords.size() > 1) {
            double wordScore = dice(leftWords, rightWords);
            return wordScore * 0.75D + characterScore * 0.25D;
        }
        return characterScore;
    }

    private double dice(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0D;
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return 2D * intersection.size() / (left.size() + right.size());
    }

    private Set<String> words(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}_]+")) {
            if (!token.isBlank()) result.add(token);
        }
        return result;
    }

    private Set<String> bigrams(String value) {
        String normalized = text(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < normalized.length() - 1; index++) {
            result.add(normalized.substring(index, index + 2));
        }
        return result;
    }

    private int iterableSize(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return 0;
        int size = 0;
        for (Object ignored : iterable) size++;
        return size;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        return "true".equalsIgnoreCase(text(value));
    }

    private double bounded(double value) {
        if (!Double.isFinite(value)) return 0D;
        return Math.max(0D, Math.min(1D, value));
    }

    private List<SkillEvolutionHintSnapshot> safeHints(
            List<SkillEvolutionHintSnapshot> hints) {
        return hints == null ? List.of() : hints.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String firstText(Object... values) {
        if (values == null) return "";
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ScoredHint(
            SkillEvolutionHintSnapshot hint,
            String content,
            String sourceKey,
            boolean current,
            boolean hardCase,
            boolean eligible,
            double relevance,
            double evidenceQuality,
            double recency,
            double baseScore) {
    }
}
