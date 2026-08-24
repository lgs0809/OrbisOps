package cn.lgs.orbisops.application.agentdefinition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDefinitionQueryUseCaseTest {

    @Test
    void listsOnlyProjectScopedDefinitionsAndFindsByStableIdentity() {
        @SuppressWarnings("unchecked")
        AgentDefinitionQueryPort<Definition, String> port = mock(AgentDefinitionQueryPort.class);
        AgentDefinitionQueryUseCase<Definition, String> useCase = new AgentDefinitionQueryUseCase<>(port);
        Definition project = new Definition("agent-1", true);
        Definition global = new Definition("global", false);
        when(port.listAll()).thenReturn(List.of(project, global));
        when(port.projectScoped(project)).thenReturn(true);
        when(port.projectScoped(global)).thenReturn(false);
        when(port.agentId(project)).thenReturn("agent-1");
        when(port.agentId(global)).thenReturn("global");

        assertEquals(List.of(project), useCase.listProjectAgents());
        assertEquals(project, useCase.find(" agent-1 "));
        assertNull(useCase.find("missing"));
    }

    @Test
    void adminDetailFindsNewestDraftWithoutRequiringAPublishedCurrentPointer() {
        @SuppressWarnings("unchecked")
        AgentDefinitionQueryPort<Definition, String> port = mock(AgentDefinitionQueryPort.class);
        var useCase = new AgentDefinitionQueryUseCase<>(port);
        Definition draft = new Definition("draft-only", true);
        when(port.listVersions("draft-only")).thenReturn(List.of(draft));
        when(port.listAll()).thenReturn(List.of());
        assertEquals(draft, useCase.find("draft-only"));
        verify(port, org.mockito.Mockito.never()).listAll();
        assertNull(useCase.find(""));
    }

    @Test
    void bindingsPreferStoredRowsAndFallBackToDerivedDefinition() {
        @SuppressWarnings("unchecked")
        AgentDefinitionQueryPort<Definition, String> port = mock(AgentDefinitionQueryPort.class);
        AgentDefinitionQueryUseCase<Definition, String> useCase = new AgentDefinitionQueryUseCase<>(port);
        when(port.storedBindings("agent-1")).thenReturn(List.of("stored"));
        assertEquals(List.of("stored"), useCase.bindings("agent-1"));

        Definition definition = new Definition("agent-2", true);
        when(port.storedBindings("agent-2")).thenReturn(List.of());
        when(port.resolveDraft("agent-2")).thenReturn(definition);
        when(port.deriveBindings(definition)).thenReturn(List.of("derived"));
        assertEquals(List.of("derived"), useCase.bindings("agent-2"));
        verify(port).resolveDraft("agent-2");
    }

    @Test
    void projectAndBindingQueriesPreserveRequiredFieldMessages() {
        @SuppressWarnings("unchecked")
        AgentDefinitionQueryPort<Definition, String> port = mock(AgentDefinitionQueryPort.class);
        AgentDefinitionQueryUseCase<Definition, String> useCase = new AgentDefinitionQueryUseCase<>(port);

        assertEquals("查询 Agent 必须提供 projectId",
                assertThrows(IllegalArgumentException.class,
                        () -> useCase.listProjectAgents(" ")).getMessage());
        assertEquals("查询 Agent 绑定必须提供 agentId",
                assertThrows(IllegalArgumentException.class,
                        () -> useCase.bindings(null)).getMessage());
    }

    private record Definition(String id, boolean projectScoped) {}
}
