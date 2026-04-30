package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalDraft;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionSignalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillEvolutionSignalPolicyTest {

    private final SkillEvolutionSignalPolicy policy = new SkillEvolutionSignalPolicy();

    @Test
    void normalizesDraftAndPreservesHistoricalSignalHashInputOrder() {
        String payload = "{\"memoryId\":\"memory-1\",\"content\":\"先看日志再看指标\"}";
        SkillEvolutionSignalDraft draft = policy.draft(
                " USER_ASSERTED_PROCEDURE ",
                " demo-project ",
                " agent-1 ",
                " run-1 ",
                " session-1 ",
                payload);

        assertEquals("USER_ASSERTED_PROCEDURE", draft.signalType());
        assertEquals("demo-project", draft.projectId());
        assertEquals("agent-1", draft.agentId());
        assertEquals("run-1", draft.runId());
        assertEquals("session-1", draft.sessionId());
        assertEquals(payload, draft.payloadJson());
        assertEquals(
                "43936f8fedcaf6f59928e9932b18efc4a67867b22437ea5f50d51db17e9c3f24",
                policy.signalIdempotencyKey(draft));
    }

    @Test
    void createsHistoricalHintPrefixAndThirtyTwoHexSuffix() {
        String payload = "{\"memoryId\":\"memory-1\",\"content\":\"先看日志再看指标\"}";

        assertEquals(
                "skill-hint-36de22b1bb31f523483aecb2f23c5d7b",
                policy.hintId("signal-1", "USER_ASSERTED_PROCEDURE", payload));
    }

    @Test
    void ownsStatusLimitAndConsumableIdRules() {
        assertEquals("CREATED", policy.createdStatus());
        assertEquals(1, policy.pendingHintLimit(0));
        assertEquals(50, policy.pendingHintLimit(50));
        assertEquals(100, policy.pendingHintLimit(1000));
        assertEquals(List.of("hint-1", "hint-2"),
                policy.consumableHintIds(List.of(" hint-1 ", "", "hint-1", "hint-2")));
        assertEquals(List.of(), policy.consumableHintIds(null));
    }

    @Test
    void defaultsBlankJsonAndRejectsMissingSignalTypeOrDraft() {
        assertEquals("{}", policy.draft("TYPE", "", "", "", "", " ").payloadJson());
        assertThrows(IllegalArgumentException.class,
                () -> policy.draft(" ", "", "", "", "", "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.signalIdempotencyKey(null));
    }
}
