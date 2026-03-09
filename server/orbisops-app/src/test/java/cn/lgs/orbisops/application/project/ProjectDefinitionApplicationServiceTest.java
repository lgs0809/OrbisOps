package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectDefinitionApplicationServiceTest {

    @Test
    void exposesProjectDefaultsAndEnvironmentsFromTypedDefinition() {
        IProjectDefinitionRepository repository = mock(IProjectDefinitionRepository.class);
        ProjectDefinition definition = new ProjectDefinition(
                "order",
                "Order",
                "",
                "alice",
                List.of("dev", "prod"),
                "order-kb",
                "order-agent",
                List.of(),
                List.of(),
                true,
                LocalDateTime.now(),
                LocalDateTime.now());
        when(repository.find("order")).thenReturn(Optional.of(definition));
        when(repository.exists("order")).thenReturn(true);
        ProjectDefinitionApplicationService service =
                new ProjectDefinitionApplicationService(repository, ignored -> { });

        assertTrue(service.exists("order"));
        assertEquals("order-agent", service.defaultAgentId("order"));
        assertEquals("order-kb", service.defaultKnowledgeBaseId("order"));
        assertEquals(List.of("dev", "prod"), service.environments("order"));
    }

    @Test
    void returnsEmptyDefaultsForBlankOrMissingProject() {
        IProjectDefinitionRepository repository = mock(IProjectDefinitionRepository.class);
        when(repository.find("missing")).thenReturn(Optional.empty());
        ProjectDefinitionApplicationService service =
                new ProjectDefinitionApplicationService(repository, ignored -> { });

        assertFalse(service.exists(""));
        assertEquals("", service.defaultAgentId(""));
        assertEquals("", service.defaultKnowledgeBaseId("missing"));
        assertEquals(List.of(), service.environments("missing"));
    }
}
