package cn.lgs.orbisops.application.agentdefinition;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDefinitionBindingUpdateUseCaseTest {

    @Test
    void updatesValidatesSavesReadsEffectiveBindingsAndAuditsInStableOrder() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle =
                mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionBindingUpdatePort<Definition, Binding, List<String>> port =
                mock(AgentDefinitionBindingUpdatePort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionBindingUpdateAuditPort<Definition, List<String>> audit =
                mock(AgentDefinitionBindingUpdateAuditPort.class);
        AgentDefinitionBindingUpdateUseCase<Definition, Binding, List<String>> useCase =
                new AgentDefinitionBindingUpdateUseCase<>(lifecycle, port, audit);
        Definition source = new Definition("source");
        Definition updated = new Definition("updated");
        Definition normalized = new Definition("normalized");
        Definition saved = new Definition("saved");
        List<Binding> bindings = List.of(new Binding("skill-1"));
        List<String> effective = List.of("skill-1");
        when(port.resolveDraft("agent-1")).thenReturn(source);
        when(port.applyBindings(source, bindings)).thenReturn(updated);
        when(port.normalizeExecutionShape(updated)).thenReturn(normalized);
        when(lifecycle.saveDraft(normalized)).thenReturn(saved);
        when(port.effectiveBindings(saved)).thenReturn(effective);

        AgentDefinitionBindingUpdateResult<Definition, List<String>> result =
                useCase.update(new AgentDefinitionBindingUpdateCommand<>(" agent-1 ", bindings));

        assertSame(saved, result.definition());
        assertEquals(effective, result.effectiveBindings());
        InOrder order = inOrder(port, lifecycle, audit);
        order.verify(port).resolveDraft("agent-1");
        order.verify(port).applyBindings(source, bindings);
        order.verify(port).assertProjectAndBindingsValid(updated);
        order.verify(port).normalizeExecutionShape(updated);
        order.verify(lifecycle).saveDraft(normalized);
        order.verify(port).effectiveBindings(saved);
        order.verify(audit).recordBindingUpdate("agent-1", saved, effective);
    }

    @Test
    void missingDefinitionFailsBeforeMutationSaveOrAudit() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle =
                mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionBindingUpdatePort<Definition, Binding, List<String>> port =
                mock(AgentDefinitionBindingUpdatePort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionBindingUpdateAuditPort<Definition, List<String>> audit =
                mock(AgentDefinitionBindingUpdateAuditPort.class);
        AgentDefinitionBindingUpdateUseCase<Definition, Binding, List<String>> useCase =
                new AgentDefinitionBindingUpdateUseCase<>(lifecycle, port, audit);
        when(port.resolveDraft("missing")).thenReturn(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.update(new AgentDefinitionBindingUpdateCommand<>(
                        "missing",
                        List.of(new Binding("skill-1")))));

        assertEquals("Agent 不存在：missing", error.getMessage());
        verify(port, never()).applyBindings(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
        verify(lifecycle, never()).saveDraft(org.mockito.ArgumentMatchers.any());
        verify(audit, never()).recordBindingUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void commandPreservesLegacyAgentIdValidation() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentDefinitionBindingUpdateCommand<String>("", List.of()));

        assertEquals("更新 Agent 绑定必须提供 agentId", error.getMessage());
    }

    private record Definition(String id) {
    }

    private record Binding(String id) {
    }
}
