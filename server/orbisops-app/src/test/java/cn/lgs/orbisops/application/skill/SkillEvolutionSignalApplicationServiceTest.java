package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionSignalRepository;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionSignalPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillEvolutionSignalApplicationServiceTest {

    @Test
    void recordsIdempotentSignalAndAuditsStoredIdentity() {
        ISkillEvolutionSignalRepository repository = mock(ISkillEvolutionSignalRepository.class);
        SkillEvolutionSignalAuditPort audit = mock(SkillEvolutionSignalAuditPort.class);
        when(repository.available()).thenReturn(true);
        when(repository.saveIdempotent(any())).thenAnswer(invocation -> {
            SkillEvolutionSignalSnapshot requested = invocation.getArgument(0);
            return new SkillEvolutionSignalSnapshot(
                    "signal-existing",
                    requested.idempotencyKey(),
                    requested.projectId(),
                    requested.agentId(),
                    requested.runId(),
                    requested.sessionId(),
                    requested.signalType(),
                    requested.payloadJson(),
                    requested.status(),
                    null);
        });
        SkillEvolutionSignalApplicationService service = service(repository, audit);

        SkillEvolutionSignalSnapshot result = service.record(new SkillEvolutionSignalCommand(
                "USER_ASSERTED_PROCEDURE",
                "demo-project",
                "agent-1",
                "run-1",
                "session-1",
                "{\"content\":\"procedure\"}"));

        assertEquals("signal-existing", result.signalId());
        assertEquals("CREATED", result.status());
        ArgumentCaptor<SkillEvolutionSignalSnapshot> captor =
                ArgumentCaptor.forClass(SkillEvolutionSignalSnapshot.class);
        verify(repository).saveIdempotent(captor.capture());
        assertEquals("skill-signal-1", captor.getValue().signalId());
        verify(audit).recordSignalCreated(result);
    }

    @Test
    void createsTypedHintQueriesPendingAndConsumesDistinctIds() {
        ISkillEvolutionSignalRepository repository = mock(ISkillEvolutionSignalRepository.class);
        SkillEvolutionSignalAuditPort audit = mock(SkillEvolutionSignalAuditPort.class);
        when(repository.available()).thenReturn(true);
        SkillEvolutionHintSnapshot pending = new SkillEvolutionHintSnapshot(
                "hint-pending", "signal-1", "demo-project", "run-1",
                "TYPE", "{}", "CREATED", null);
        when(repository.findPendingHints("demo-project", 100)).thenReturn(List.of(pending));
        SkillEvolutionSignalApplicationService service = service(repository, audit);

        SkillEvolutionHintSnapshot created = service.createHint(new SkillEvolutionHintCommand(
                "signal-1", "demo-project", "run-1", "TYPE", "{}"));
        List<SkillEvolutionHintSnapshot> hints = service.pendingHints(" demo-project ", 200);
        List<String> consumed = service.markHintsConsumed(
                List.of(" hint-1 ", "", "hint-1", "hint-2"),
                "candidate-1");

        assertEquals("CREATED", created.status());
        assertEquals(List.of(pending), hints);
        assertEquals(List.of("hint-1", "hint-2"), consumed);
        verify(repository).saveHintIdempotent(created);
        verify(repository).markHintConsumed("hint-1");
        verify(repository).markHintConsumed("hint-2");
        verify(audit).recordHintsConsumed("candidate-1", List.of("hint-1", "hint-2"));
    }

    @Test
    void blankProjectAndEmptyIdsShortCircuitWithoutAudit() {
        ISkillEvolutionSignalRepository repository = mock(ISkillEvolutionSignalRepository.class);
        SkillEvolutionSignalAuditPort audit = mock(SkillEvolutionSignalAuditPort.class);
        when(repository.available()).thenReturn(true);
        SkillEvolutionSignalApplicationService service = service(repository, audit);

        assertEquals(List.of(), service.pendingHints(" ", 10));
        assertEquals(List.of(), service.markHintsConsumed(List.of("", " "), "candidate"));
        verify(repository, never()).findPendingHints(any(), anyInt());
        verify(audit, never()).recordHintsConsumed(any(), any());
    }

    @Test
    void missingRepositoryAndEmptyGeneratedIdFailClosed() {
        ISkillEvolutionSignalRepository unavailable = mock(ISkillEvolutionSignalRepository.class);
        when(unavailable.available()).thenReturn(false);
        SkillEvolutionSignalApplicationService unavailableService = service(unavailable, null);
        assertEquals("Skill Evolution Signal Store 未配置",
                assertThrows(IllegalStateException.class,
                        () -> unavailableService.record(new SkillEvolutionSignalCommand(
                                "TYPE", "p", "a", "r", "s", "{}"))).getMessage());

        ISkillEvolutionSignalRepository repository = mock(ISkillEvolutionSignalRepository.class);
        when(repository.available()).thenReturn(true);
        SkillEvolutionSignalApplicationService emptyIdService = new SkillEvolutionSignalApplicationService(
                repository,
                new SkillEvolutionSignalPolicy(),
                () -> " ",
                null);
        assertEquals("SKILL_SIGNAL_ID_EMPTY",
                assertThrows(IllegalStateException.class,
                        () -> emptyIdService.record(new SkillEvolutionSignalCommand(
                                "TYPE", "p", "a", "r", "s", "{}"))).getMessage());
    }

    private SkillEvolutionSignalApplicationService service(
            ISkillEvolutionSignalRepository repository,
            SkillEvolutionSignalAuditPort audit) {
        return new SkillEvolutionSignalApplicationService(
                repository,
                new SkillEvolutionSignalPolicy(),
                () -> "skill-signal-1",
                audit);
    }
}
