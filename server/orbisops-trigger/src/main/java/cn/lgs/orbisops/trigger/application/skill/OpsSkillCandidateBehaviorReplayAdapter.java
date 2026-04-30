package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillBehaviorReplayApplicationService;
import cn.lgs.orbisops.application.skill.SkillBehaviorReplayRequest;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolResult;
import cn.lgs.orbisops.application.skill.SkillCandidateBehaviorReplayPort;
import cn.lgs.orbisops.application.skill.SkillCandidateTournamentContext;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSet;
import cn.lgs.orbisops.application.skill.SkillTournamentCandidate;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorFrozenSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict Tournament behavior verifier over frozen three-arm hidden and mutation fixtures. */
@Component
public final class OpsSkillCandidateBehaviorReplayAdapter
        implements SkillCandidateBehaviorReplayPort {

    private final SkillBehaviorReplayApplicationService replay;

    public OpsSkillCandidateBehaviorReplayAdapter(
            SkillBehaviorReplayApplicationService replay) {
        if (replay == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_REPLAY_SERVICE_REQUIRED");
        }
        this.replay = replay;
    }

    @Override
    public SkillBehaviorEvaluation verify(
            SkillTournamentCandidate candidate,
            SkillHiddenEvaluationSet evaluationSet,
            SkillVerifierVersion verifierVersion) {
        throw new IllegalStateException("SKILL_BEHAVIOR_CONTEXT_REQUIRED");
    }

    @Override
    public SkillBehaviorEvaluation verify(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate,
            SkillHiddenEvaluationSet evaluationSet) {
        if (context == null || candidate == null || evaluationSet == null) {
            throw new IllegalArgumentException("SKILL_BEHAVIOR_INPUT_REQUIRED");
        }
        List<Map<String, Object>> cases = new ArrayList<>(evaluationSet.hiddenCases());
        cases.addAll(evaluationSet.mutationCases());
        if (cases.isEmpty()) {
            return failed(context, candidate, "SKILL_BEHAVIOR_CASES_REQUIRED");
        }
        List<SkillBehaviorEvaluation> evaluations = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            Map<String, Object> evalCase = cases.get(index);
            try {
                evaluations.add(replay.evaluate(request(
                        context, candidate, evaluationSet, evalCase, index)));
            } catch (RuntimeException error) {
                failures.add("SKILL_BEHAVIOR_CASE_INVALID:"
                        + caseId(evalCase, index) + ":" + error.getClass().getSimpleName());
            }
        }
        if (evaluations.isEmpty()) {
            return failed(context, candidate,
                    failures.isEmpty() ? "SKILL_BEHAVIOR_NO_VALID_CASES" : failures.get(0));
        }
        evaluations.forEach(item -> failures.addAll(item.reasonCodes()));
        return aggregate(context, candidate, evaluations, failures);
    }

    private SkillBehaviorReplayRequest request(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate,
            SkillHiddenEvaluationSet evaluationSet,
            Map<String, Object> evalCase,
            int index) {
        String caseId = caseId(evalCase, index);
        Map<String, Object> arms = map(evalCase.get("arms"));
        EnumMap<SkillBehaviorReplayArm, Map<String, Object>> fixtures =
                new EnumMap<>(SkillBehaviorReplayArm.class);
        for (SkillBehaviorReplayArm arm : SkillBehaviorReplayArm.values()) {
            Object fixture = arms.get(arm.name());
            if (fixture == null) fixture = evalCase.get(field(arm));
            Map<String, Object> mapped = map(fixture);
            if (mapped.isEmpty()) {
                throw new IllegalArgumentException("SKILL_BEHAVIOR_ARM_FIXTURE_REQUIRED:" + arm);
            }
            fixtures.put(arm, mapped);
        }
        String inputHash = hashOrCanonical(evalCase.get("inputHash"),
                first(evalCase.get("input"), evalCase));
        String toolSnapshotHash = hashOrCanonical(
                evalCase.get("toolSnapshotHash"),
                first(evalCase.get("toolSnapshot"), Map.of("suite", evaluationSet.suiteId())));
        String mcpSchemaHash = hashOrCanonical(
                evalCase.get("mcpSchemaHash"),
                first(evalCase.get("mcpSchema"), Map.of("suite", evaluationSet.suiteId())));
        SkillBehaviorFrozenSnapshot frozen = new SkillBehaviorFrozenSnapshot(
                text(evalCase.get("modelId"), "frozen-eval-model"),
                text(evalCase.get("modelVersion"), context.verifierVersion().behaviorVersion()),
                decimal(evalCase.get("temperature"), 0D),
                longValue(evalCase.get("seed"), 0L),
                inputHash,
                toolSnapshotHash,
                mcpSchemaHash,
                context.baseSkillHash(),
                candidate.candidateHash(),
                context.verifierVersion().behaviorVersion());
        String replayId = context.tournamentId() + ":" + candidate.candidateId() + ":" + index;
        return new SkillBehaviorReplayRequest(
                replayId,
                replayId + ":evaluation",
                evaluationSet.suiteId() + ":" + caseId,
                context.projectId(),
                "skill-tournament",
                "skill-tournament-" + context.skillId(),
                context.tournamentId(),
                context.skillId(),
                SkillBehaviorReplayExecutionMode.FROZEN_MOCK,
                frozen,
                fixtures,
                frozenToolResults(evalCase.get("frozenToolResults")),
                decimal(evalCase.get("minimumSuccessDelta"), 0D),
                longValue(evalCase.get("maximumCostMicros"), Long.MAX_VALUE));
    }

    private SkillBehaviorEvaluation aggregate(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate,
            List<SkillBehaviorEvaluation> evaluations,
            List<String> failures) {
        List<SkillBehaviorReplayResult> noSkill = evaluations.stream()
                .map(SkillBehaviorEvaluation::noSkill).toList();
        List<SkillBehaviorReplayResult> baseline = evaluations.stream()
                .map(SkillBehaviorEvaluation::baseline).toList();
        List<SkillBehaviorReplayResult> next = evaluations.stream()
                .map(SkillBehaviorEvaluation::candidate).toList();
        List<String> reasons = failures.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        boolean admitted = evaluations.stream().allMatch(SkillBehaviorEvaluation::admitted)
                && reasons.isEmpty();
        return new SkillBehaviorEvaluation(
                context.tournamentId() + ":" + candidate.candidateId() + ":aggregate",
                aggregateArm(SkillBehaviorReplayArm.NO_SKILL, "", noSkill),
                aggregateArm(SkillBehaviorReplayArm.BASELINE, context.baseSkillHash(), baseline),
                aggregateArm(SkillBehaviorReplayArm.CANDIDATE, candidate.candidateHash(), next),
                admitted,
                reasons,
                context.verifierVersion().behaviorVersion());
    }

    private SkillBehaviorReplayResult aggregateArm(
            SkillBehaviorReplayArm arm,
            String activeSkillHash,
            List<SkillBehaviorReplayResult> results) {
        List<SkillBehaviorMetrics> metrics = results.stream()
                .map(SkillBehaviorReplayResult::metrics)
                .toList();
        int size = Math.max(1, metrics.size());
        SkillBehaviorMetrics aggregated = new SkillBehaviorMetrics(
                metrics.stream().mapToDouble(SkillBehaviorMetrics::successRate).sum() / size,
                metrics.stream().mapToInt(SkillBehaviorMetrics::safetyViolationCount).sum(),
                metrics.stream().mapToInt(SkillBehaviorMetrics::toolCallCount).sum(),
                metrics.stream().mapToInt(SkillBehaviorMetrics::invalidToolCallCount).sum(),
                metrics.stream().mapToDouble(SkillBehaviorMetrics::evidenceCompleteness).sum() / size,
                metrics.stream().mapToDouble(SkillBehaviorMetrics::hallucinationRate).sum() / size,
                metrics.stream().mapToLong(SkillBehaviorMetrics::latencyMs).sum(),
                metrics.stream().mapToLong(SkillBehaviorMetrics::tokenCount).sum(),
                metrics.stream().mapToLong(SkillBehaviorMetrics::costMicros).sum(),
                metrics.stream().mapToDouble(SkillBehaviorMetrics::finalAnswerQuality).sum() / size,
                metrics.stream().mapToDouble(SkillBehaviorMetrics::routingAccuracy).sum() / size);
        return new SkillBehaviorReplayResult(
                arm,
                activeSkillHash,
                aggregated,
                CanonicalObjectHasher.sha256(results.stream()
                        .map(SkillBehaviorReplayResult::finalAnswerHash).toList()),
                CanonicalObjectHasher.sha256(results.stream()
                        .map(SkillBehaviorReplayResult::evidenceHash).toList()),
                results.stream().flatMap(item -> item.toolResultIds().stream()).distinct().sorted().toList(),
                results.stream().flatMap(item -> item.reasonCodes().stream()).distinct().sorted().toList());
    }

    private SkillBehaviorEvaluation failed(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate,
            String reason) {
        SkillBehaviorMetrics zero = new SkillBehaviorMetrics(
                0D, 0, 0, 0, 0D, 0D, 0L, 0L, 0L, 0D, 0D);
        return new SkillBehaviorEvaluation(
                context.tournamentId() + ":" + candidate.candidateId() + ":failed",
                result(SkillBehaviorReplayArm.NO_SKILL, "", zero, reason),
                result(SkillBehaviorReplayArm.BASELINE, context.baseSkillHash(), zero, reason),
                result(SkillBehaviorReplayArm.CANDIDATE, candidate.candidateHash(), zero, reason),
                false,
                List.of(reason),
                context.verifierVersion().behaviorVersion());
    }

    private SkillBehaviorReplayResult result(
            SkillBehaviorReplayArm arm,
            String hash,
            SkillBehaviorMetrics metrics,
            String reason) {
        return new SkillBehaviorReplayResult(
                arm,
                hash,
                metrics,
                CanonicalObjectHasher.sha256(arm.name() + ":failed-answer"),
                CanonicalObjectHasher.sha256(arm.name() + ":failed-evidence"),
                List.of(),
                List.of(reason));
    }

    private Map<String, SkillBehaviorToolResult> frozenToolResults(Object value) {
        Map<String, Object> source = map(value);
        if (source.isEmpty()) return Map.of();
        Map<String, SkillBehaviorToolResult> result = new LinkedHashMap<>();
        source.forEach((resultId, raw) -> {
            Map<String, Object> item = map(raw);
            result.put(resultId, new SkillBehaviorToolResult(
                    resultId,
                    bool(item.get("allowed"), false),
                    hashOrCanonical(item.get("outputHash"), item.get("payload")),
                    map(item.get("payload"))));
        });
        return Collections.unmodifiableMap(result);
    }

    private String caseId(Map<String, Object> evalCase, int index) {
        return text(evalCase.get("caseId"), "case-" + index);
    }

    private String field(SkillBehaviorReplayArm arm) {
        return switch (arm) {
            case NO_SKILL -> "noSkill";
            case BASELINE -> "baseline";
            case CANDIDATE -> "candidate";
        };
    }

    private String hashOrCanonical(Object hash, Object source) {
        String supplied = text(hash, "").toLowerCase();
        return supplied.matches("[a-f0-9]{64}")
                ? supplied
                : CanonicalObjectHasher.sha256(source == null ? Map.of() : source);
    }

    private Object first(Object first, Object second) {
        return first == null ? second : first;
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Collections.unmodifiableMap(result);
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = text(value, "");
        return text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(text(value, ""));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value, ""));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
