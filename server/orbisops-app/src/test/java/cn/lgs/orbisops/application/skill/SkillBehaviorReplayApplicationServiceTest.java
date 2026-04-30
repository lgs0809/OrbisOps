package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorFrozenSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillBehaviorReplayApplicationServiceTest {

    @Test
    void frozenThreeArmReplayMustNotCallRealToolsAndCanAdmitCandidate() {
        AtomicInteger actualCalls = new AtomicInteger();
        SkillBehaviorReplayPort replay = replayPort(call(
                true, false, false, false, "frozen-1"));
        SkillBehaviorReplayApplicationService service =
                new SkillBehaviorReplayApplicationService(replay, request -> {
                    actualCalls.incrementAndGet();
                    return toolResult("actual");
                });

        SkillBehaviorEvaluation evaluation = service.evaluate(request(
                SkillBehaviorReplayExecutionMode.FROZEN_MOCK,
                Map.of("frozen-1", toolResult("frozen-1")), 0.10, 2_000));

        assertTrue(evaluation.admitted());
        assertEquals(0, actualCalls.get());
        assertEquals(List.of("frozen-1"), evaluation.candidate().toolResultIds());
        assertEquals("verifier-v1", evaluation.verifierVersion());
    }

    @Test
    void readOnlyModeMustRejectWriteTool() {
        SkillBehaviorReplayPort replay = replayPort(call(
                false, false, false, false, ""));
        SkillBehaviorReplayApplicationService service =
                new SkillBehaviorReplayApplicationService(replay, request -> toolResult("actual"));

        SecurityException error = assertThrows(SecurityException.class, () -> service.evaluate(
                request(SkillBehaviorReplayExecutionMode.READ_ONLY, Map.of(), 0.05, 2_000)));

        assertEquals("SKILL_REPLAY_READ_ONLY_TOOL_REQUIRED", error.getMessage());
    }

    @Test
    void sandboxMustAllowProposalButNeverProductionWriteOrLanding() {
        AtomicInteger calls = new AtomicInteger();
        SkillBehaviorReplayApplicationService sandbox =
                new SkillBehaviorReplayApplicationService(
                        replayPort(call(false, false, true, false, "")),
                        request -> {
                            calls.incrementAndGet();
                            return toolResult("sandbox-" + calls.get());
                        });

        SkillBehaviorEvaluation evaluation = sandbox.evaluate(request(
                SkillBehaviorReplayExecutionMode.SANDBOX_DRY_RUN,
                Map.of(), 0.10, 2_000));
        assertTrue(evaluation.admitted());
        assertEquals(3, calls.get());

        for (SkillBehaviorToolCall forbidden : List.of(
                call(false, true, true, false, ""),
                call(false, false, true, true, ""))) {
            SkillBehaviorReplayApplicationService service =
                    new SkillBehaviorReplayApplicationService(
                            replayPort(forbidden), request -> toolResult("never"));
            SecurityException error = assertThrows(SecurityException.class, () -> service.evaluate(
                    request(SkillBehaviorReplayExecutionMode.SANDBOX_DRY_RUN,
                            Map.of(), 0.10, 2_000)));
            assertEquals(SkillBehaviorReplayApplicationService.PRODUCTION_WRITE_FORBIDDEN,
                    error.getMessage());
        }
    }

    @Test
    void candidateSafetyAndQualityRegressionsMustFailAdmission() {
        SkillBehaviorReplayPort replay = (request, tools) -> {
            SkillBehaviorMetrics metrics = switch (request.arm()) {
                case NO_SKILL -> metrics(0.20, 0, 0.30, 0.10, 0.40, 0.30, 500);
                case BASELINE -> metrics(0.70, 0, 0.80, 0.05, 0.80, 0.85, 900);
                case CANDIDATE -> metrics(0.72, 1, 0.70, 0.10, 0.75, 0.80, 2_500);
            };
            return result(request, metrics, List.of());
        };
        SkillBehaviorReplayApplicationService service =
                new SkillBehaviorReplayApplicationService(replay, request -> toolResult("unused"));

        SkillBehaviorEvaluation evaluation = service.evaluate(request(
                SkillBehaviorReplayExecutionMode.FROZEN_MOCK, Map.of(), 0.05, 2_000));

        assertEquals(false, evaluation.admitted());
        assertTrue(evaluation.reasonCodes().contains("SKILL_REPLAY_SAFETY_REGRESSION"));
        assertTrue(evaluation.reasonCodes().contains("SKILL_REPLAY_SUCCESS_DELTA_NOT_MET"));
        assertTrue(evaluation.reasonCodes().contains("SKILL_REPLAY_ROUTING_REGRESSION"));
        assertTrue(evaluation.reasonCodes().contains("SKILL_REPLAY_EVIDENCE_REGRESSION"));
        assertTrue(evaluation.reasonCodes().contains("SKILL_REPLAY_HALLUCINATION_REGRESSION"));
        assertTrue(evaluation.reasonCodes().contains("SKILL_REPLAY_ANSWER_QUALITY_REGRESSION"));
        assertTrue(evaluation.reasonCodes().contains("SKILL_REPLAY_COST_BUDGET_EXCEEDED"));
    }

    private SkillBehaviorReplayPort replayPort(SkillBehaviorToolCall call) {
        return (request, tools) -> {
            SkillBehaviorToolResult tool = tools.execute(new SkillBehaviorToolExecutionRequest(
                    request.replayId(), request.projectId(), request.actor(), request.sessionId(),
                    request.runId(), request.arm(), request.mode(), 0, call));
            SkillBehaviorMetrics metrics = switch (request.arm()) {
                case NO_SKILL -> metrics(0.20, 0, 0.30, 0.10, 0.40, 0.30, 500);
                case BASELINE -> metrics(0.60, 0, 0.70, 0.05, 0.70, 0.75, 900);
                case CANDIDATE -> metrics(0.80, 0, 0.85, 0.03, 0.85, 0.90, 1_100);
            };
            return result(request, metrics, List.of(tool.resultId()));
        };
    }

    private SkillBehaviorReplayResult result(
            SkillBehaviorReplayArmRequest request,
            SkillBehaviorMetrics metrics,
            List<String> resultIds) {
        return new SkillBehaviorReplayResult(
                request.arm(), request.activeSkillHash(), metrics,
                CanonicalObjectHasher.sha256(request.arm().name() + ":answer"),
                CanonicalObjectHasher.sha256(request.arm().name() + ":evidence"),
                resultIds, List.of());
    }

    private SkillBehaviorMetrics metrics(
            double success,
            int safety,
            double evidence,
            double hallucination,
            double answerQuality,
            double routing,
            long cost) {
        return new SkillBehaviorMetrics(
                success, safety, 1, 0, evidence, hallucination,
                100, 200, cost, answerQuality, routing);
    }

    private SkillBehaviorReplayRequest request(
            SkillBehaviorReplayExecutionMode mode,
            Map<String, SkillBehaviorToolResult> frozen,
            double delta,
            long costBudget) {
        EnumMap<SkillBehaviorReplayArm, Map<String, Object>> fixtures =
                new EnumMap<>(SkillBehaviorReplayArm.class);
        for (SkillBehaviorReplayArm arm : SkillBehaviorReplayArm.values()) {
            fixtures.put(arm, Map.of());
        }
        return new SkillBehaviorReplayRequest(
                "replay-1", "evaluation-1", "fixture-1", "project-1", "actor-1",
                "session-1", "run-1", "skill-1", mode,
                new SkillBehaviorFrozenSnapshot(
                        "model-1", "v1", 0D, 42L,
                        "input-hash", "tool-snapshot-hash", "mcp-schema-hash",
                        "baseline-hash", "candidate-hash", "verifier-v1"),
                fixtures, frozen, delta, costBudget);
    }

    private SkillBehaviorToolCall call(
            boolean readOnly,
            boolean productionWrite,
            boolean proposal,
            boolean directLanding,
            String frozenResultId) {
        return new SkillBehaviorToolCall(
                "toolset-1", "inspect", Map.of("target", "db"),
                readOnly, productionWrite, proposal, directLanding, frozenResultId);
    }

    private SkillBehaviorToolResult toolResult(String id) {
        return new SkillBehaviorToolResult(
                id, true, CanonicalObjectHasher.sha256(id), Map.of("status", "ok"));
    }
}
