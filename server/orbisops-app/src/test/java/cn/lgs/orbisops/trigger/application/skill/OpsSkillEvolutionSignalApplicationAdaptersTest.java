package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionHintCommand;
import cn.lgs.orbisops.application.skill.SkillEvolutionSignalCommand;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsSkillEvolutionSignalApplicationAdaptersTest {

    @Test
    void mapperOwnsFastJsonCommandsAndLegacyViews() {
        OpsSkillEvolutionSignalMapper mapper = new OpsSkillEvolutionSignalMapper();

        SkillEvolutionSignalCommand signalCommand = mapper.signalCommand(
                "TYPE", "demo-project", "agent-1", "run-1", "session-1",
                Map.of("content", "procedure"));
        SkillEvolutionHintCommand hintCommand = mapper.hintCommand(
                "signal-1", "demo-project", "run-1", "TYPE",
                Map.of("content", "hint"));
        Map<String, Object> signalView = mapper.signalView(signal());
        Map<String, Object> hintView = mapper.hintView(hint());
        List<Map<String, Object>> pending = mapper.pendingHintViews(List.of(hint()));

        assertTrue(signalCommand.payloadJson().contains("\"content\":\"procedure\""));
        assertTrue(hintCommand.contentJson().contains("\"content\":\"hint\""));
        assertEquals("signal-1", signalView.get("signalId"));
        assertEquals("hint-1", hintView.get("hintId"));
        assertEquals("hint-1", pending.get(0).get("hint_id"));
        assertEquals("hint", ((Map<?, ?>) pending.get(0).get("content")).get("content"));
    }

    @Test
    void identityAdapterPreservesHistoricalPrefix() {
        OpsSkillEvolutionSignalIdentityAdapter adapter =
                new OpsSkillEvolutionSignalIdentityAdapter();

        assertTrue(adapter.newSignalId().startsWith("skill-signal-"));
    }

    @Test
    void auditAdapterPreservesRuntimeAndConsumedAuditShapes() {
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsSkillEvolutionSignalAuditAdapter adapter =
                new OpsSkillEvolutionSignalAuditAdapter(auditService);

        adapter.recordSignalCreated(signal());
        adapter.recordHintsConsumed(" candidate-1 ", List.of("hint-1", "hint-2"));

        verify(auditService).recordRuntimeEvent(
                "demo-project",
                "agent-1",
                "",
                "skill-evolution",
                "signal-created",
                "signal-1",
                "LOW",
                "CREATED",
                Map.of("signalType", "TYPE", "runId", "run-1"));
        verify(auditService).record(
                eq(""),
                eq("skill-evolution"),
                eq("hints-consumed"),
                eq("candidate-1"),
                isNull(),
                eq(Map.of(
                        "candidateId", "candidate-1",
                        "hintIds", List.of("hint-1", "hint-2"))));
    }

    private SkillEvolutionSignalSnapshot signal() {
        return new SkillEvolutionSignalSnapshot(
                "signal-1", "idem-1", "demo-project", "agent-1", "run-1", "session-1",
                "TYPE", "{\"content\":\"procedure\"}", "CREATED",
                Instant.parse("2026-07-22T01:00:00Z"));
    }

    private SkillEvolutionHintSnapshot hint() {
        return new SkillEvolutionHintSnapshot(
                "hint-1", "signal-1", "demo-project", "run-1", "TYPE",
                "{\"content\":\"hint\"}", "CREATED",
                Instant.parse("2026-07-22T01:10:00Z"));
    }
}
