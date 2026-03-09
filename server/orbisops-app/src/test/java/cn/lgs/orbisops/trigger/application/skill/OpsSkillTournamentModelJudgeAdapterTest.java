package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillCandidateTournamentContext;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate;
import cn.lgs.orbisops.application.skill.SkillHiddenEvaluationSet;
import cn.lgs.orbisops.application.skill.SkillTournamentCandidate;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeDisposition;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillTournamentModelJudgeAdapterTest {

    private static final String BASE_HASH = "a".repeat(64);
    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("structural-v1", "behavior-v1", "judge-frozen-v7");

    @Test
    void unavailableLlmMustReturnUnavailableWithoutCallingModel() {
        OpsAgentLlmClient llm = mock(OpsAgentLlmClient.class);
        when(llm.available()).thenReturn(false);

        SkillModelJudgeEvaluation result = judge(llm);

        assertEquals(SkillModelJudgeDisposition.UNAVAILABLE, result.disposition());
        assertEquals(List.of("SKILL_JUDGE_MODEL_UNAVAILABLE"), result.reasonCodes());
        assertEquals("judge-frozen-v7", result.judgeVersion());
        verify(llm, never()).chatJsonObject(anyString(), anyString(), anyString());
    }

    @Test
    void degradedProtocolResponseMustReturnUnavailable() {
        SkillModelJudgeEvaluation result = judge(response(Map.of(
                "degraded", true,
                "disposition", "PASS")));

        assertEquals(SkillModelJudgeDisposition.UNAVAILABLE, result.disposition());
        assertEquals(List.of("SKILL_JUDGE_PROTOCOL_UNAVAILABLE"), result.reasonCodes());
    }

    @Test
    void invalidDispositionMustRequireManualReview() {
        SkillModelJudgeEvaluation result = judge(response(Map.of(
                "disposition", "UNKNOWN",
                "score", 0.7D,
                "reasons", List.of("protocol ambiguity"))));

        assertEquals(SkillModelJudgeDisposition.MANUAL_REVIEW, result.disposition());
        assertEquals(List.of("protocol ambiguity"), result.reasonCodes());
    }

    @Test
    void passWithReasonsMustDowngradeToManualReview() {
        SkillModelJudgeEvaluation result = judge(response(Map.of(
                "disposition", "PASS",
                "score", 0.9D,
                "reasons", List.of("uncertain evidence"))));

        assertEquals(SkillModelJudgeDisposition.MANUAL_REVIEW, result.disposition());
        assertTrue(result.reasonCodes().contains("uncertain evidence"));
        assertTrue(result.reasonCodes().contains("SKILL_JUDGE_PASS_WITH_REASONS"));
    }

    @Test
    void failWithoutReasonsMustReceiveStableReasonCode() {
        SkillModelJudgeEvaluation result = judge(response(Map.of(
                "disposition", "FAIL",
                "score", 0.2D,
                "reasons", List.of())));

        assertEquals(SkillModelJudgeDisposition.FAIL, result.disposition());
        assertEquals(List.of("SKILL_JUDGE_REASON_REQUIRED"), result.reasonCodes());
    }

    @Test
    void scoreMustBeBoundedToZeroAndOne() {
        SkillModelJudgeEvaluation high = judge(response(Map.of(
                "disposition", "PASS",
                "score", 9D,
                "reasons", List.of())));
        SkillModelJudgeEvaluation low = judge(response(Map.of(
                "disposition", "FAIL",
                "score", -9D,
                "reasons", List.of("failed"))));

        assertEquals(1D, high.score());
        assertEquals(0D, low.score());
    }

    @Test
    void judgeVersionMustAlwaysComeFromFrozenTournamentContext() {
        OpsAgentLlmClient llm = response(Map.of(
                "disposition", "PASS",
                "score", 0.8D,
                "reasons", List.of()));

        SkillModelJudgeEvaluation result = judge(llm);

        assertEquals("judge-frozen-v7", result.judgeVersion());
        verify(llm).chatJsonObject(
                org.mockito.ArgumentMatchers.eq("skill-candidate-tournament-judge"),
                anyString(),
                anyString());
    }

    private SkillModelJudgeEvaluation judge(OpsAgentLlmClient llm) {
        return new OpsSkillTournamentModelJudgeAdapter(llm).judge(
                context(), candidate(), behavior(), suite());
    }

    private OpsAgentLlmClient response(Map<String, Object> values) {
        OpsAgentLlmClient llm = mock(OpsAgentLlmClient.class);
        when(llm.available()).thenReturn(true);
        JSONObject result = JSON.parseObject(JSON.toJSONString(values));
        when(llm.chatJsonObject(anyString(), anyString(), anyString())).thenReturn(result);
        return llm;
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

    private SkillBehaviorEvaluation behavior() {
        SkillTournamentCandidate candidate = candidate();
        return new SkillBehaviorEvaluation(
                "evaluation-1",
                replay(SkillBehaviorReplayArm.NO_SKILL, "", 0.2D),
                replay(SkillBehaviorReplayArm.BASELINE, BASE_HASH, 0.6D),
                replay(SkillBehaviorReplayArm.CANDIDATE, candidate.candidateHash(), 0.9D),
                true,
                List.of(),
                VERSION.behaviorVersion());
    }

    private SkillBehaviorReplayResult replay(
            SkillBehaviorReplayArm arm,
            String activeHash,
            double success) {
        return new SkillBehaviorReplayResult(
                arm,
                activeHash,
                new SkillBehaviorMetrics(
                        success, 0, 1, 0, 0.9D, 0.01D,
                        100L, 200L, 300L, 0.9D, 0.9D),
                CanonicalObjectHasher.sha256(arm.name() + ":answer"),
                CanonicalObjectHasher.sha256(arm.name() + ":evidence"),
                List.of(),
                List.of());
    }

    private SkillHiddenEvaluationSet suite() {
        List<Map<String, Object>> hidden = List.of(Map.of("caseId", "hidden-1"));
        List<Map<String, Object>> mutation = List.of(Map.of("caseId", "mutation-1"));
        return new SkillHiddenEvaluationSet(
                "suite-v1",
                hidden,
                mutation,
                CanonicalObjectHasher.sha256(hidden),
                CanonicalObjectHasher.sha256(mutation));
    }
}
