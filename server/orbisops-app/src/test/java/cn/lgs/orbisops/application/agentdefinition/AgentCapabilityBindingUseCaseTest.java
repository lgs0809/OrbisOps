package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBindingSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCapabilityBindingUseCaseTest {

    @Test
    void unavailableStoreKeepsQueriesEmptyAndWritesFailClosed() {
        IAgentCapabilityBindingRepository repository = mock(IAgentCapabilityBindingRepository.class);
        AgentCapabilityBindingUseCase useCase = new AgentCapabilityBindingUseCase(repository);
        AgentCapabilityBindingSnapshot snapshot = snapshot();

        assertTrue(useCase.latest("agent-a").isEmpty());
        assertThrows(IllegalStateException.class, () -> useCase.replace(snapshot));
    }

    @Test
    void delegatesTypedQueryAndReplacementToRepository() {
        IAgentCapabilityBindingRepository repository = mock(IAgentCapabilityBindingRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.findLatest("agent-a")).thenReturn(List.of());
        AgentCapabilityBindingUseCase useCase = new AgentCapabilityBindingUseCase(repository);
        AgentCapabilityBindingSnapshot snapshot = snapshot();

        useCase.latest(" agent-a ");
        useCase.replace(snapshot);

        verify(repository).findLatest("agent-a");
        verify(repository).replace(snapshot);
    }

    private AgentCapabilityBindingSnapshot snapshot() {
        return new AgentCapabilityBindingSnapshot(
                "agent-a",
                1,
                AgentDefinitionLifecycle.DRAFT,
                "project-a",
                List.of());
    }
}
