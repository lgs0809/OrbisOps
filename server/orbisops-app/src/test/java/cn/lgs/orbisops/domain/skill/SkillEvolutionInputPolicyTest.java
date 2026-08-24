package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInput;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionMessage;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionTraceEvent;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionInputPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionInputPolicyTest {

    private final SkillEvolutionInputPolicy policy = new SkillEvolutionInputPolicy();

    @Test
    void summarizesTraceMessagesEvidenceAndContextBundle() {
        Map<String, Object> toolPayload = new LinkedHashMap<>();
        toolPayload.put("evidenceId", " evidence-1 ");
        toolPayload.put("resultId", " result-1 ");
        toolPayload.put("outputHash", " hash-1 ");
        toolPayload.put("contextBundleHash", " context-1 ");

        SkillEvolutionInputSummary summary = policy.summarize(new SkillEvolutionInput(
                List.of(
                        new SkillEvolutionTraceEvent("TOOL_FINISHED", "SUCCEEDED", " tool output ", toolPayload),
                        new SkillEvolutionTraceEvent("FINAL_OUTPUT", "SUCCEEDED", "first report", Map.of()),
                        new SkillEvolutionTraceEvent("RUN_FINISHED", "SUCCEEDED", " final report ", Map.of())),
                List.of(
                        new SkillEvolutionMessage("user", "first goal"),
                        new SkillEvolutionMessage("assistant", "ignored"),
                        new SkillEvolutionMessage(" USER ", " final goal "))));

        assertAll(
                () -> assertEquals(List.of(
                        "TOOL_FINISHED:SUCCEEDED:tool output",
                        "FINAL_OUTPUT:SUCCEEDED:first report",
                        "RUN_FINISHED:SUCCEEDED:final report"), summary.eventSummaries()),
                () -> assertEquals(List.of("tool output"), summary.toolEvents()),
                () -> assertEquals("final report", summary.finalReport()),
                () -> assertEquals("final goal", summary.normalizedUserGoal()),
                () -> assertTrue(summary.hasCompleted()),
                () -> assertTrue(summary.hasToolEvidence()),
                () -> assertTrue(summary.hasMessages()),
                () -> assertEquals(List.of(new SkillEvolutionEvidenceReference(
                        "evidence-1", "result-1", "hash-1")), summary.evidenceReferences()),
                () -> assertEquals("context-1", summary.contextBundleHash()));
    }

    @Test
    void prefersFinalOutputContentOverGenericRuntimeSummary() {
        String report = "根因是采集目标不可达。先核对 Prometheus 自身健康，再检查 up 指标和错误日志，最后验证恢复状态并记录证据。";

        SkillEvolutionInputSummary summary = policy.summarize(new SkillEvolutionInput(
                List.of(
                        new SkillEvolutionTraceEvent(
                                "TOOL_FINISHED", "SUCCEEDED", "prometheus ok", "", Map.of(
                                        "evidenceId", "e1", "resultId", "r1", "outputHash", "h1")),
                        new SkillEvolutionTraceEvent(
                                "FINAL_OUTPUT", "SUCCEEDED", "Agent 输出完成。", report, Map.of())),
                List.of(new SkillEvolutionMessage("user", "帮我查线上异常"))));

        assertEquals(report, summary.finalReport());
    }

    @Test
    void appliesHistoricalLimitsAndUserGoalAbbreviation() {
        List<SkillEvolutionTraceEvent> trace = new ArrayList<>();
        for (int index = 0; index < 85; index++) {
            trace.add(new SkillEvolutionTraceEvent("TOOL_" + index, "DONE", "tool-" + index, Map.of()));
        }
        String goal = "x".repeat(1001);

        SkillEvolutionInputSummary summary = policy.summarize(new SkillEvolutionInput(
                trace,
                List.of(new SkillEvolutionMessage("user", goal))));

        assertAll(
                () -> assertEquals(80, summary.eventSummaries().size()),
                () -> assertEquals(20, summary.toolEvents().size()),
                () -> assertEquals(1003, summary.normalizedUserGoal().length()),
                () -> assertTrue(summary.normalizedUserGoal().endsWith("...")),
                () -> assertEquals("ab...", policy.abbreviate("abcdef", 2)));
    }

    @Test
    void durableWorkflowQuestionCountsAsConversationWhenChatSessionIsEmpty() {
        SkillEvolutionInputSummary summary = policy.summarize(new SkillEvolutionInput(
                List.of(
                        new SkillEvolutionTraceEvent(
                                "NODE_STARTED", "RUNNING", "plan", Map.of("question", "排查 join 5xx 并形成证据")),
                        new SkillEvolutionTraceEvent(
                                "TOOL_FINISHED", "SUCCEEDED", "prometheus ok", Map.of(
                                        "evidenceId", "e1", "resultId", "r1", "outputHash", "h1")),
                        new SkillEvolutionTraceEvent("RUN_FINISHED", "SUCCEEDED", "done", Map.of())),
                List.of()));

        assertAll(
                () -> assertEquals("排查 join 5xx 并形成证据", summary.normalizedUserGoal()),
                () -> assertTrue(summary.hasMessages()),
                () -> assertEquals("", policy.skipReason(summary)));
    }

    @Test
    void arbitraryToolPayloadMustNotBePromotedToConversation() {
        SkillEvolutionInputSummary summary = policy.summarize(new SkillEvolutionInput(
                List.of(
                        new SkillEvolutionTraceEvent(
                                "TOOL_CALL_STARTED", "RUNNING", "tool", Map.of("question", "forged tool question")),
                        new SkillEvolutionTraceEvent("RUN_FINISHED", "SUCCEEDED", "done", Map.of())),
                List.of()));

        assertAll(
                () -> assertEquals("", summary.normalizedUserGoal()),
                () -> assertFalse(summary.hasMessages()),
                () -> assertEquals("SKIP_NO_CONVERSATION", policy.skipReason(summary)));
    }

    @Test
    void nullInputProducesEmptyNonCompletedSummary() {
        SkillEvolutionInputSummary summary = policy.summarize(null);

        assertAll(
                () -> assertEquals(List.of(), summary.eventSummaries()),
                () -> assertEquals(List.of(), summary.toolEvents()),
                () -> assertEquals("", summary.normalizedUserGoal()),
                () -> assertEquals("", summary.finalReport()),
                () -> assertFalse(summary.hasCompleted()),
                () -> assertFalse(summary.hasToolEvidence()),
                () -> assertFalse(summary.hasMessages()),
                () -> assertEquals(List.of(), summary.evidenceReferences()),
                () -> assertEquals("", summary.contextBundleHash()));
    }
}
