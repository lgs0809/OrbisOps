package cn.lgs.orbisops.application.agentdefinition;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDefinitionCloneUseCaseTest {

    @Test
    void clonesThroughProjectSanitizationDraftSaveAndAuditInStableOrder() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle =
                mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionClonePort<Definition> port = mock(AgentDefinitionClonePort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionCloneAuditPort<Definition> audit = mock(AgentDefinitionCloneAuditPort.class);
        AgentDefinitionCloneUseCase<Definition> useCase =
                new AgentDefinitionCloneUseCase<>(lifecycle, port, audit);
        Definition source = new Definition("source");
        Definition sanitized = new Definition("sanitized");
        Definition prepared = new Definition("prepared");
        Definition normalized = new Definition("normalized");
        Definition saved = new Definition("saved");
        when(port.requireExistingProject("project-2")).thenReturn("project-2");
        when(port.resolveSource("agent-1")).thenReturn(source);
        when(port.sanitizeForProject(source, "project-2")).thenReturn(sanitized);
        when(port.prepareClone(
                sanitized,
                "project-2",
                "agent-2",
                "目标 Agent")).thenReturn(prepared);
        when(port.normalizeExecutionShape(prepared)).thenReturn(normalized);
        when(lifecycle.saveDraft(normalized)).thenReturn(saved);

        Definition result = useCase.clone(new AgentDefinitionCloneCommand(
                " agent-1 ",
                " project-2 ",
                " agent-2 ",
                " 目标 Agent "));

        assertSame(saved, result);
        InOrder order = inOrder(port, lifecycle, audit);
        order.verify(port).requireExistingProject("project-2");
        order.verify(port).resolveSource("agent-1");
        order.verify(port).sanitizeForProject(source, "project-2");
        order.verify(port).prepareClone(sanitized, "project-2", "agent-2", "目标 Agent");
        order.verify(port).normalizeExecutionShape(prepared);
        order.verify(lifecycle).saveDraft(normalized);
        order.verify(audit).recordClone("agent-1", "agent-2", saved);
    }

    @Test
    void missingSourceFailsBeforeMutationOrAudit() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle =
                mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionClonePort<Definition> port = mock(AgentDefinitionClonePort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionCloneAuditPort<Definition> audit = mock(AgentDefinitionCloneAuditPort.class);
        AgentDefinitionCloneUseCase<Definition> useCase =
                new AgentDefinitionCloneUseCase<>(lifecycle, port, audit);
        when(port.requireExistingProject("project-2")).thenReturn("project-2");
        when(port.resolveSource("missing")).thenReturn(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.clone(new AgentDefinitionCloneCommand(
                        "missing",
                        "project-2",
                        "agent-2",
                        "")));

        assertEquals("源 Agent 不存在：missing", error.getMessage());
        verify(port, never()).sanitizeForProject(null, "project-2");
        verify(lifecycle, never()).saveDraft(org.mockito.ArgumentMatchers.any());
        verify(audit, never()).recordClone(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void commandPreservesLegacyValidationMessage() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentDefinitionCloneCommand("", "project-2", "agent-2", ""));

        assertEquals(
                "复制 Agent 必须提供 sourceAgentId、targetProjectId 和 newAgentId",
                error.getMessage());
    }

    private record Definition(String id) {
    }
}
