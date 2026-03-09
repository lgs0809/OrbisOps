package cn.lgs.orbisops.application.agentdefinition;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDefinitionLifecycleOperationUseCaseTest {

    @Test
    void transitionOperationsDelegateAndAuditWithStableIdentity() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle = mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleOperationPort<Snapshot> port = mock(AgentDefinitionLifecycleOperationPort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleOperationAuditPort<Definition, Snapshot> audit =
                mock(AgentDefinitionLifecycleOperationAuditPort.class);
        AgentDefinitionLifecycleOperationUseCase<Definition, Snapshot> useCase =
                new AgentDefinitionLifecycleOperationUseCase<>(lifecycle, port, audit);
        Definition validated = new Definition("validated");
        Definition published = new Definition("published");
        Definition rollback = new Definition("rollback");
        when(lifecycle.validate("agent-1", 3)).thenReturn(validated);
        when(lifecycle.publish("agent-1", 3)).thenReturn(published);
        when(lifecycle.rollback("agent-1", 3)).thenReturn(rollback);
        AgentDefinitionVersionCommand command = new AgentDefinitionVersionCommand(" agent-1 ", 3);

        assertSame(validated, useCase.validate(command));
        assertSame(published, useCase.publish(command));
        assertSame(rollback, useCase.rollback(command));

        InOrder order = inOrder(lifecycle, audit);
        order.verify(lifecycle).validate("agent-1", 3);
        order.verify(audit).recordTransition("validate", "agent-1", 3, validated);
        order.verify(lifecycle).publish("agent-1", 3);
        order.verify(audit).recordTransition("publish", "agent-1", 3, published);
        order.verify(lifecycle).rollback("agent-1", 3);
        order.verify(audit).recordTransition("rollback", "agent-1", 3, rollback);
    }

    @Test
    void disableReadsBeforeSnapshotBeforeLifecycleMutationAndAudit() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle = mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleOperationPort<Snapshot> port = mock(AgentDefinitionLifecycleOperationPort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleOperationAuditPort<Definition, Snapshot> audit =
                mock(AgentDefinitionLifecycleOperationAuditPort.class);
        AgentDefinitionLifecycleOperationUseCase<Definition, Snapshot> useCase =
                new AgentDefinitionLifecycleOperationUseCase<>(lifecycle, port, audit);
        Snapshot before = new Snapshot("before");
        when(port.currentSnapshot("agent-1")).thenReturn(before);
        when(lifecycle.disable("agent-1", 4)).thenReturn(true);

        assertTrue(useCase.disable(new AgentDefinitionVersionCommand("agent-1", 4)));

        InOrder order = inOrder(port, lifecycle, audit);
        order.verify(port).currentSnapshot("agent-1");
        order.verify(lifecycle).disable("agent-1", 4);
        order.verify(audit).recordDisable("agent-1", 4, before, true);
    }

    @Test
    void versionCommandPreservesVersionValidationCode() {
        IllegalArgumentException nullError = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentDefinitionVersionCommand("agent-1", (Integer) null));
        IllegalArgumentException zeroError = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentDefinitionVersionCommand("agent-1", 0));

        assertEquals("AGENT_DEFINITION_VERSION_INVALID", nullError.getMessage());
        assertEquals("AGENT_DEFINITION_VERSION_INVALID", zeroError.getMessage());
    }

    private record Definition(String id) {}
    private record Snapshot(String id) {}
}
