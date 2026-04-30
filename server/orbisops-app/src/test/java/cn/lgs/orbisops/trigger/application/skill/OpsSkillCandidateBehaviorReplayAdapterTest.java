package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillBehaviorReplayApplicationService;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolCall;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolExecutionRequest;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolResult;
import cn.lgs.orbisops.application.skill.SkillCandidateTournamentContext;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSet;
import cn.lgs.orbisops.application.skill.SkillTournamentCandidate;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillCandidateBehaviorReplayAdapterTest {

    private static final String BASE_HASH = "a".repeat(64);
    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("structural-v1", "behavior-v1", "judge-v1");

    @Test
    void hiddenAndMutationCasesMustAggregateAllThreeArmsInFrozenMockMode() {
        List<SkillBehaviorReplayExecutionMode> modes = new ArrayList<>();
        OpsSkillCandidateBehaviorReplayAdapter adapter = adapter((request, tools) -> {
            modes.add(request.mode());
            return replayResult(request.arm(), request.activeSkillHash(), request.fixture());
        });
        SkillHiddenEvaluationSet suite = suite(
                caseWithSuccess("hidden-1", 0.8D),
                caseWithSuccess("mutation-1", 1.0D));

        SkillBehaviorEvaluation result = adapter.verify(context(), candidate(), suite);

        assertTrue(result.admitted());
        assertEquals(6, modes.size());
        assertTrue(modes.stream().allMatch(mode ->
                mode == SkillBehaviorReplayExecutionMode.FROZEN_MOCK));
        assertEquals(0.9D, result.candidate().metrics().successRate(), 1e-9D);
        assertEquals(2, result.candidate().metrics().toolCallCount());
        assertEquals(200L, result.candidate().metrics().latencyMs());
        assertEquals(candidate().candidateHash(), result.candidate().activeSkillHash());
        assertEquals(BASE_HASH, result.baseline().activeSkillHash());
    }

    @Test
    void missingNoSkillFixtureMustRejectWholeTournamentCandidate() {
        assertMissingArmRejected(SkillBehaviorReplayArm.NO_SKILL);
    }

    @Test
    void missingBaselineFixtureMustRejectWholeTournamentCandidate() {
        assertMissingArmRejected(SkillBehaviorReplayArm.BASELINE);
    }

    @Test
    void missingCandidateFixtureMustRejectWholeTournamentCandidate() {
        assertMissingArmRejected(SkillBehaviorReplayArm.CANDIDATE);
    }

    @Test
    void frozenToolResultMustBeParsedAndServedWithoutProductionExecution() {
        List<SkillBehaviorToolResult> observed = new ArrayList<>();
        OpsSkillCandidateBehaviorReplayAdapter adapter = adapter((request, tools) -> {
            if (Boolean.TRUE.equals(request.fixture().get("invokeTool"))) {
                SkillBehaviorToolResult result = tools.execute(toolRequest(request, "result-1"));
                observed.add(result);
            }
            return replayResult(request.arm(), request.activeSkillHash(), request.fixture());
        });
        Map<String, Object> hidden = caseWithSuccess("hidden-tool", 0.9D);
        arm(hidden, SkillBehaviorReplayArm.CANDIDATE).put("invokeTool", true);
        hidden.put("frozenToolResults", Map.of(
                "result-1", Map.of(
                        "allowed", true,
                        "payload", Map.of("rows", 3))));

        SkillBehaviorEvaluation result = adapter.verify(
                context(), candidate(), suite(hidden, caseWithSuccess("mutation-1", 0.9D)));

        assertTrue(result.admitted());
        assertEquals(1, observed.size());
        assertTrue(observed.get(0).allowed());
        assertEquals(3, observed.get(0).payload().get("rows"));
        assertEquals(CanonicalObjectHasher.sha256(Map.of("rows", 3)),
                observed.get(0).outputHash());
    }

    @Test
    void missingFrozenToolResultMustRejectCandidate() {
        OpsSkillCandidateBehaviorReplayAdapter adapter = adapter((request, tools) -> {
            if (Boolean.TRUE.equals(request.fixture().get("invokeTool"))) {
                tools.execute(toolRequest(request, "missing-result"));
            }
            return replayResult(request.arm(), request.activeSkillHash(), request.fixture());
        });
        Map<String, Object> hidden = caseWithSuccess("hidden-missing-tool", 0.9D);
        arm(hidden, SkillBehaviorReplayArm.CANDIDATE).put("invokeTool", true);

        SkillBehaviorEvaluation result = adapter.verify(
                context(), candidate(), suite(hidden, caseWithSuccess("mutation-1", 0.9D)));

        assertFalse(result.admitted());
        assertTrue(result.reasonCodes().stream().anyMatch(reason ->
                reason.startsWith("SKILL_BEHAVIOR_CASE_INVALID:hidden-missing-tool:")));
    }

    @Test
    void oneMalformedCaseMustRejectOtherwiseSuccessfulAggregate() {
        OpsSkillCandidateBehaviorReplayAdapter adapter = adapter((request, tools) ->
                replayResult(request.arm(), request.activeSkillHash(), request.fixture()));
        Map<String, Object> malformed = caseWithSuccess("mutation-malformed", 0.9D);
        arms(malformed).remove(SkillBehaviorReplayArm.CANDIDATE.name());

        SkillBehaviorEvaluation result = adapter.verify(
                context(), candidate(), suite(
                        caseWithSuccess("hidden-valid", 0.9D), malformed));

        assertFalse(result.admitted());
        assertEquals(0.9D, result.candidate().metrics().successRate(), 1e-9D);
        assertTrue(result.reasonCodes().stream().anyMatch(reason ->
                reason.startsWith("SKILL_BEHAVIOR_CASE_INVALID:mutation-malformed:")));
    }

    @Test
    void candidateReplayActiveHashMustEqualTournamentCandidateHash() {
        OpsSkillCandidateBehaviorReplayAdapter adapter = adapter((request, tools) -> {
            String activeHash = request.arm() == SkillBehaviorReplayArm.CANDIDATE
                    ? "wrong-candidate-hash"
                    : request.activeSkillHash();
            return replayResult(request.arm(), activeHash, request.fixture());
        });

        SkillBehaviorEvaluation result = adapter.verify(
                context(), candidate(), suite(
                        caseWithSuccess("hidden-1", 0.9D),
                        caseWithSuccess("mutation-1", 0.9D)));

        assertFalse(result.admitted());
        assertTrue(result.reasonCodes().stream().anyMatch(reason ->
                reason.contains("SKILL_BEHAVIOR_CASE_INVALID")));
    }

    private void assertMissingArmRejected(SkillBehaviorReplayArm missingArm) {
        OpsSkillCandidateBehaviorReplayAdapter adapter = adapter((request, tools) ->
                replayResult(request.arm(), request.activeSkillHash(), request.fixture()));
        Map<String, Object> malformed = caseWithSuccess("hidden-missing-" + missingArm.name(), 0.9D);
        arms(malformed).remove(missingArm.name());

        SkillBehaviorEvaluation result = adapter.verify(
                context(), candidate(), suite(
                        malformed,
                        caseWithSuccess("mutation-valid", 0.9D)));

        assertFalse(result.admitted());
        assertTrue(result.reasonCodes().stream().anyMatch(reason ->
                reason.startsWith("SKILL_BEHAVIOR_CASE_INVALID:hidden-missing-"
                        + missingArm.name() + ":")));
    }

    private OpsSkillCandidateBehaviorReplayAdapter adapter(
            cn.lgs.orbisops.application.skill.SkillBehaviorReplayPort replayPort) {
        SkillBehaviorReplayApplicationService service =
                new SkillBehaviorReplayApplicationService(
                        replayPort,
                        request -> {
                            throw new AssertionError(
                                    "FROZEN_MOCK must not invoke production tool execution");
                        });
        return new OpsSkillCandidateBehaviorReplayAdapter(service);
    }

    private SkillBehaviorReplayResult replayResult(
            SkillBehaviorReplayArm arm,
            String activeHash,
            Map<String, Object> fixture) {
        double success = decimal(fixture.get("success"));
        double evidence = decimal(fixture.getOrDefault("evidence", 0.9D));
        double hallucination = decimal(fixture.getOrDefault("hallucination", 0.01D));
        double quality = decimal(fixture.getOrDefault("quality", 0.9D));
        double routing = decimal(fixture.getOrDefault("routing", 0.9D));
        int safety = integer(fixture.getOrDefault("safety", 0));
        int toolCalls = integer(fixture.getOrDefault("toolCalls", 1));
        int invalidCalls = integer(fixture.getOrDefault("invalidCalls", 0));
        long latency = longValue(fixture.getOrDefault("latency", 100L));
        long tokens = longValue(fixture.getOrDefault("tokens", 200L));
        long cost = longValue(fixture.getOrDefault("cost", 300L));
        return new SkillBehaviorReplayResult(
                arm,
                activeHash,
                new SkillBehaviorMetrics(
                        success, safety, toolCalls, invalidCalls,
                        evidence, hallucination, latency, tokens, cost,
                        quality, routing),
                CanonicalObjectHasher.sha256(arm.name() + ":answer:" + success),
                CanonicalObjectHasher.sha256(arm.name() + ":evidence:" + success),
                List.of(),
                List.of());
    }

    private SkillBehaviorToolExecutionRequest toolRequest(
            cn.lgs.orbisops.application.skill.SkillBehaviorReplayArmRequest request,
            String frozenResultId) {
        return new SkillBehaviorToolExecutionRequest(
                request.replayId(),
                request.projectId(),
                request.actor(),
                request.sessionId(),
                request.runId(),
                request.arm(),
                request.mode(),
                0,
                new SkillBehaviorToolCall(
                        "toolset-1",
                        "query",
                        Map.of("q", "diagnose"),
                        true,
                        false,
                        false,
                        false,
                        frozenResultId));
    }

    private SkillHiddenEvaluationSet suite(
            Map<String, Object> hidden,
            Map<String, Object> mutation) {
        return new SkillHiddenEvaluationSet(
                "suite-v1",
                List.of(hidden),
                List.of(mutation),
                CanonicalObjectHasher.sha256(List.of(hidden)),
                CanonicalObjectHasher.sha256(List.of(mutation)));
    }

    private Map<String, Object> caseWithSuccess(
            String caseId,
            double candidateSuccess) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", caseId);
        result.put("input", Map.of("prompt", caseId));
        Map<String, Object> arms = new LinkedHashMap<>();
        arms.put(SkillBehaviorReplayArm.NO_SKILL.name(), fixture(0.2D));
        arms.put(SkillBehaviorReplayArm.BASELINE.name(), fixture(0.6D));
        arms.put(SkillBehaviorReplayArm.CANDIDATE.name(), fixture(candidateSuccess));
        result.put("arms", arms);
        result.put("minimumSuccessDelta", 0D);
        result.put("maximumCostMicros", 10_000L);
        return result;
    }

    private Map<String, Object> fixture(double success) {
        Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("success", success);
        fixture.put("evidence", 0.9D);
        fixture.put("hallucination", 0.01D);
        fixture.put("quality", 0.9D);
        fixture.put("routing", 0.9D);
        fixture.put("safety", 0);
        fixture.put("toolCalls", 1);
        fixture.put("invalidCalls", 0);
        fixture.put("latency", 100L);
        fixture.put("tokens", 200L);
        fixture.put("cost", 300L);
        return fixture;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> arms(Map<String, Object> evalCase) {
        return (Map<String, Object>) evalCase.get("arms");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> arm(
            Map<String, Object> evalCase,
            SkillBehaviorReplayArm arm) {
        return (Map<String, Object>) arms(evalCase).get(arm.name());
    }

    private SkillCandidateTournamentContext context() {
        return new SkillCandidateTournamentContext(
                "tournament-1",
                "project-1",
                "skill-1",
                7,
                BASE_HASH,
                "suite-v1",
                VERSION);
    }

    private SkillTournamentCandidate candidate() {
        Map<String, Object> payload = Map.of(
                "patchType", "PATCH",
                "changes", List.of(Map.of(
                        "section", "procedure",
                        "key", "step-1",
                        "operation", "REPLACE")),
                "evalCases", List.of(Map.of("caseId", "eval-1")),
                "authoringSource", "TEST");
        SkillEvolutionAuthoredCandidate authored = SkillEvolutionAuthoredCandidate.from(payload);
        return new SkillTournamentCandidate(
                "candidate-1",
                CanonicalObjectHasher.sha256(payload),
                authored,
                1);
    }

    private double decimal(Object value) {
        return ((Number) value).doubleValue();
    }

    private int integer(Object value) {
        return ((Number) value).intValue();
    }

    private long longValue(Object value) {
        return ((Number) value).longValue();
    }
}
