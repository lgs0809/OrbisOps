package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityBinding;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityOwnerType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityScope;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsAgentCapabilityBindingQueryAdapterTest {

    @Test
    void blankAgentIdReturnsEmptyWithoutStoreAccess() {
        IAgentCapabilityBindingRepository repository =
                mock(IAgentCapabilityBindingRepository.class);
        OpsAgentCapabilityBindingQueryAdapter adapter =
                new OpsAgentCapabilityBindingQueryAdapter(repository);

        assertTrue(adapter.list(" ").isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void mapsTypedBindingsToLegacyResponseShape() {
        IAgentCapabilityBindingRepository repository =
                mock(IAgentCapabilityBindingRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.findLatest("agent-a")).thenReturn(List.of(new AgentCapabilityBinding(
                7L,
                "agent-a",
                3,
                AgentDefinitionLifecycle.PUBLISHED,
                "project-a",
                AgentCapabilityOwnerType.NODE,
                "diagnose",
                AgentCapabilityType.INLINE_MCP_SERVER,
                "inline-query",
                AgentCapabilityScope.INLINE,
                Map.of("transport", "http"),
                "system",
                Instant.parse("2026-07-21T00:00:00Z"))));
        OpsAgentCapabilityBindingQueryAdapter adapter =
                new OpsAgentCapabilityBindingQueryAdapter(repository);

        Map<String, Object> binding = adapter.list(" agent-a ").get(0);

        assertEquals("agent-a", binding.get("agentId"));
        assertEquals("inline_mcp_server", binding.get("capabilityType"));
        assertEquals("INLINE", binding.get("capabilityScope"));
        assertEquals("{\"transport\":\"http\"}", binding.get("bindConfigJson"));
    }

    @Test
    void dataAccessFailureReturnsEmptyCompatibilityResult() {
        IAgentCapabilityBindingRepository repository =
                mock(IAgentCapabilityBindingRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.findLatest("agent-a"))
                .thenThrow(new DataAccessResourceFailureException("store unavailable"));
        OpsAgentCapabilityBindingQueryAdapter adapter =
                new OpsAgentCapabilityBindingQueryAdapter(repository);

        assertTrue(adapter.list("agent-a").isEmpty());
        assertTrue(adapter.list("agent-a").isEmpty());
    }
}
