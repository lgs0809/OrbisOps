package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionHintSelectorDeterminismTest {

    private final SkillEvolutionPayloadCodec codec = new SkillEvolutionPayloadCodec();
    private final SkillEvolutionHintSelector selector = new SkillEvolutionHintSelector(codec);

    @Test
    void selectionMustBeIndependentFromRepositoryOrderAndSuppressDuplicates() {
        List<SkillEvolutionHintSnapshot> hints = List.of(
                hint("hint-current", "signal-current", "run-current", "USER",
                        Map.of("content", "mysql timeout repair with readonly evidence first",
                                "sourceType", "USER", "evidenceQuality", 0.8), 6),
                hint("hint-duplicate", "signal-old", "run-old", "USER",
                        Map.of("content", "mysql timeout repair with readonly evidence first",
                                "sourceType", "USER", "evidenceQuality", 0.7), 5),
                hint("hint-hard", "signal-hard", "run-hard", "EVAL",
                        Map.of("content", "mysql timeout counterexample requires connection pool inspection",
                                "sourceType", "HIDDEN_EVAL", "evidenceQuality", 0.95,
                                "hardCase", true), 4),
                hint("hint-diverse", "signal-metric", "run-metric", "OBSERVATION",
                        Map.of("content", "mysql timeout trend correlates with datasource saturation",
                                "sourceType", "METRIC", "evidenceRefs", List.of("e1", "e2")), 3),
                hint("hint-procedure", "signal-procedure", "run-procedure", "USER",
                        Map.of("content", "mysql timeout inspect logs then datasource then retry policy",
                                "sourceType", "OPERATOR", "trustedEvidence", true), 2),
                hint("hint-irrelevant", "signal-irrelevant", "run-irrelevant", "USER",
                        Map.of("content", "write a poem about summer", "sourceType", "USER"), 1));

        List<String> expected = ids(selector.select(
                hints, "diagnose mysql timeout and repair", "signal-current",
                "run-current", "TOOL_FAILURE"));
        for (int seed = 1; seed <= 20; seed++) {
            List<SkillEvolutionHintSnapshot> shuffled = new ArrayList<>(hints);
            Collections.shuffle(shuffled, new Random(seed));
            assertEquals(expected, ids(selector.select(
                    shuffled, "diagnose mysql timeout and repair", "signal-current",
                    "run-current", "TOOL_FAILURE")));
        }

        assertEquals("hint-current", expected.get(0));
        assertTrue(expected.contains("hint-hard"));
        assertTrue(expected.contains("hint-diverse"));
        assertFalse(expected.contains("hint-duplicate"));
        assertFalse(expected.contains("hint-irrelevant"));
    }

    @Test
    void hardCaseQuotaMustNotBypassEvidenceThreshold() {
        List<SkillEvolutionHintSnapshot> hints = List.of(
                hint("hard-trusted", "s1", "r1", "EVAL",
                        Map.of("content", "redis timeout hard counterexample",
                                "hardCase", true, "evidenceQuality", 0.8), 2),
                hint("hard-untrusted", "s2", "r2", "EVAL",
                        Map.of("content", "unrelated hard case",
                                "hardCase", true, "evidenceQuality", 0.1), 1));

        List<String> selected = ids(selector.select(
                hints, "redis timeout", "", "", "TOOL_FAILURE"));

        assertTrue(selected.contains("hard-trusted"));
        assertFalse(selected.contains("hard-untrusted"));
    }

    private SkillEvolutionHintSnapshot hint(
            String hintId,
            String signalId,
            String runId,
            String hintType,
            Map<String, Object> payload,
            int hour) {
        return new SkillEvolutionHintSnapshot(
                hintId, signalId, "project-1", runId, hintType,
                codec.encode(payload), "PENDING",
                Instant.parse("2026-08-01T00:00:00Z").plusSeconds(hour * 3600L));
    }

    private List<String> ids(List<SkillEvolutionSelectedHint> selected) {
        return selected.stream().map(item -> item.hint().hintId()).toList();
    }
}
