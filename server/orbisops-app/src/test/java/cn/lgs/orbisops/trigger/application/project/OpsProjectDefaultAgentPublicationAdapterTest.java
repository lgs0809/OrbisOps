package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsProjectDefaultAgentPublicationAdapterTest {

    @Test
    void publishedRequiresMatchingProjectAgentEnabledAndPublishedLifecycle() {
        IAgentDefinitionRepository repository = mock(IAgentDefinitionRepository.class);
        when(repository.listCurrentEnabled()).thenReturn(List.of(
                snapshot("demo-project-agent", "demo-project", AgentDefinitionLifecycle.PUBLISHED, true),
                snapshot("draft-agent", "demo-project", AgentDefinitionLifecycle.DRAFT, true),
                snapshot("disabled-agent", "demo-project", AgentDefinitionLifecycle.PUBLISHED, false),
                snapshot("other-project-agent", "other-project", AgentDefinitionLifecycle.PUBLISHED, true)));
        OpsProjectDefaultAgentPublicationAdapter adapter =
                new OpsProjectDefaultAgentPublicationAdapter(repository);

        assertTrue(adapter.published(" demo-project ", " demo-project-agent "));
        assertFalse(adapter.published("demo-project", "draft-agent"));
        assertFalse(adapter.published("demo-project", "disabled-agent"));
        assertFalse(adapter.published("demo-project", "other-project-agent"));
        assertFalse(adapter.published("", "demo-project-agent"));
    }

    private AgentDefinitionSnapshot snapshot(
            String agentId,
            String projectId,
            AgentDefinitionLifecycle lifecycle,
            boolean enabled) {
        return new AgentDefinitionSnapshot(
                agentId,
                1,
                "hash-1",
                lifecycle,
                "Project Agent",
                projectId,
                "CHAT",
                "description",
                "instruction",
                "start",
                "{}",
                enabled,
                lifecycle == AgentDefinitionLifecycle.PUBLISHED,
                "UI");
    }
}
