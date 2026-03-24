package cn.lgs.orbisops.application.agentdefinition;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDefinitionDraftSaveUseCaseTest {

    @Test
    void validatesNormalizesSavesAndAuditsInStableOrder() {
        @SuppressWarnings("unchecked")
        AgentDefinitionLifecycleUseCase<Definition> lifecycle =
                mock(AgentDefinitionLifecycleUseCase.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionDraftSavePort<Definition> port =
                mock(AgentDefinitionDraftSavePort.class);
        @SuppressWarnings("unchecked")
        AgentDefinitionDraftSaveAuditPort<Definition> audit =
                mock(AgentDefinitionDraftSaveAuditPort.class);
        AgentDefinitionDraftSaveUseCase<Definition> useCase =
                new AgentDefinitionDraftSaveUseCase<>(lifecycle, port, audit);
        Definition source = new Definition("source");
        Definition normalized = new Definition("normalized");
        Definition saved = new Definition("saved");
        when(port.normalizeExecutionShape(source)).thenReturn(normalized);
        when(lifecycle.saveDraft(normalized)).thenReturn(saved);

        Definition result = useCase.save(
                new AgentDefinitionDraftSaveCommand<>(source, "save-draft"));

        assertSame(saved, result);
        InOrder order = inOrder(port, lifecycle, audit);
        order.verify(port).assertProjectAndBindingsValid(source);
        order.verify(port).normalizeExecutionShape(source);
        order.verify(lifecycle).saveDraft(normalized);
        order.verify(audit).recordDraftSave("save-draft", saved);
    }

    private record Definition(String id) {
    }
}
