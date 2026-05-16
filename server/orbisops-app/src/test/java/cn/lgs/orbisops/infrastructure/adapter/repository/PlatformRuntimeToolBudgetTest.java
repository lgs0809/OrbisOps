package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository.Dispatch;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformRuntimeToolBudgetTest {
    @Test void platformPostVerificationDoesNotRequireAUserPublishedWorkflowVersion() {
        var jdbc = mock(JdbcTemplate.class);
        var definitions = mock(IAgentDefinitionRepository.class);
        when(jdbc.queryForList(anyString(), eq("run"))).thenReturn(List.of(Map.of(
                "project_id", "p", "agent_id", "platform", "agent_version", 3,
                "agent_definition_hash", "frozen", "status", "SUCCEEDED", "cancel_requested", 0)));
        when(definitions.findVersion("platform", 3)).thenReturn(Optional.empty());
        var dispatch = new Dispatch("p", "run", "node", "mcp", "read", "call", 1, "rpc");
        var repository = new JdbcWorkflowToolCallBudgetRepository(jdbc, definitions,
                (id, version, hash) -> Optional.of(Map.of("definitionHash", "frozen")));
        assertEquals(0, repository.reserve(dispatch));
        assertThrows(SecurityException.class,
                () -> new JdbcWorkflowToolCallBudgetRepository(jdbc, definitions).reserve(dispatch));
        assertThrows(SecurityException.class,
                () -> new JdbcWorkflowToolCallBudgetRepository(jdbc, definitions,
                        (id, version, hash) -> Optional.of(Map.of("definitionHash", "changed"))).reserve(dispatch));
    }
}
