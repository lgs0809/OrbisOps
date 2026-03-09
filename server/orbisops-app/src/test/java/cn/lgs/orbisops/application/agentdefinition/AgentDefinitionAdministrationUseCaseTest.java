package cn.lgs.orbisops.application.agentdefinition;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDefinitionAdministrationUseCaseTest {

    @Test
    void deleteReadsSnapshotBeforeMutationAndAudit() {
        @SuppressWarnings("unchecked")
        AgentDefinitionAdministrationPort<Definition, Snapshot> port = mock(AgentDefinitionAdministrationPort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionAdministrationAuditPort<Snapshot> audit = mock(AgentDefinitionAdministrationAuditPort.class);
        AgentDefinitionAdministrationUseCase<Definition, Snapshot> useCase =
                new AgentDefinitionAdministrationUseCase<>(port, audit);
        Snapshot before = new Snapshot("before");
        when(port.currentSnapshot("agent-1")).thenReturn(before);
        when(port.delete("agent-1")).thenReturn(true);

        assertTrue(useCase.delete(" agent-1 "));

        InOrder order = inOrder(port, audit);
        order.verify(port).currentSnapshot("agent-1");
        order.verify(port).delete("agent-1");
        order.verify(audit).recordDelete("agent-1", before, true);
    }

    @Test
    void reloadPersistsAuditsThenReturnsFreshProjectDefinitions() {
        @SuppressWarnings("unchecked")
        AgentDefinitionAdministrationPort<Definition, Snapshot> port = mock(AgentDefinitionAdministrationPort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionAdministrationAuditPort<Snapshot> audit = mock(AgentDefinitionAdministrationAuditPort.class);
        AgentDefinitionAdministrationUseCase<Definition, Snapshot> useCase =
                new AgentDefinitionAdministrationUseCase<>(port, audit);
        List<Definition> fresh = List.of(new Definition("agent-1"));
        when(port.listProjectDefinitions()).thenReturn(fresh);

        assertEquals(fresh, useCase.reload());

        InOrder order = inOrder(port, audit);
        order.verify(port).reload();
        order.verify(audit).recordReload();
        order.verify(port).listProjectDefinitions();
    }

    private record Definition(String id) {}
    private record Snapshot(String id) {}
}
