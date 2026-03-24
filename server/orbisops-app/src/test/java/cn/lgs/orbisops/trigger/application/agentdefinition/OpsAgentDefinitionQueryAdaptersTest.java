package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMemoryCatalog;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionMutationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;
import cn.lgs.orbisops.testsupport.OpsAgentDefinitionValidatorTestFactory;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionQueryAdaptersTest {

    @Test
    void descriptorMapsTypedStateAndReturnsDefensiveSnapshot() {
        OpsAgentDefinitionSnapshotMapper snapshotMapper =
                new OpsAgentDefinitionSnapshotMapper();
        OpsAgentDefinitionDescriptorAdapter adapter =
                new OpsAgentDefinitionDescriptorAdapter(snapshotMapper);
        OpsAgentDefinition definition = definition("agent-a", 3, "project-a");
        definition.setDefinitionHash("hash-a");

        AgentDefinitionVersionState state = adapter.describe(definition);
        OpsAgentDefinition snapshot = adapter.snapshot(definition);

        assertEquals("agent-a", state.agentId());
        assertEquals(3, state.version());
        assertEquals("project-a", state.projectId());
        assertEquals(AgentDefinitionLifecycle.PUBLISHED, state.lifecycle());
        assertNotSame(definition, snapshot);
        snapshot.setName("changed");
        assertEquals("Agent agent-a", definition.getName());
    }

    @Test
    void catalogAdapterDelegatesCurrentVersionListsAndDefault() {
        OpsAgentDefinitionSnapshotMapper snapshotMapper =
                new OpsAgentDefinitionSnapshotMapper();
        OpsAgentDefinitionDescriptorAdapter descriptor =
                new OpsAgentDefinitionDescriptorAdapter(snapshotMapper);
        AgentDefinitionMemoryCatalog<OpsAgentDefinition> memoryCatalog =
                new AgentDefinitionMemoryCatalog<>(descriptor);
        OpsAgentDefinitionMutationAdapter mutationAdapter =
                new OpsAgentDefinitionMutationAdapter(
                        OpsAgentDefinitionValidatorTestFactory.create(),
                        snapshotMapper,
                        null,
                        null,
                        null);
        AgentDefinitionMutationService<OpsAgentDefinition> mutationService =
                new AgentDefinitionMutationService<>(
                        mutationAdapter,
                        mutationAdapter,
                        memoryCatalog);
        mutationService.registerLoaded(definition("agent-a", 1, ""), true);
        mutationService.registerLoaded(definition("agent-a", 2, ""), false);
        OpsAgentDefinitionCatalogAdapter adapter =
                new OpsAgentDefinitionCatalogAdapter(
                        memoryCatalog,
                        mutationService,
                        () -> "agent-a");

        assertEquals("agent-a", adapter.defaultAgentId());
        assertEquals(1, adapter.findCurrent("agent-a").getVersion());
        assertEquals(2, adapter.findVersion("agent-a", 2).getVersion());
        assertEquals(1, adapter.findCurrentDefinitions().size());
        assertEquals(2, adapter.findVersions("agent-a").size());
    }

    @Test
    void projectDirectorySupportsLateOptionalServiceInjection() {
        AtomicReference<ProjectDefinitionApplicationService> serviceReference =
                new AtomicReference<>();
        OpsProjectAgentDirectoryAdapter adapter =
                new OpsProjectAgentDirectoryAdapter(
                        serviceReference::get,
                        () -> "platform-default");

        assertFalse(adapter.available());
        assertFalse(adapter.exists("project-a"));
        assertEquals("platform-default", adapter.defaultAgentId("project-a"));

        ProjectDefinitionApplicationService service =
                mock(ProjectDefinitionApplicationService.class);
        when(service.exists("project-a")).thenReturn(true);
        when(service.defaultAgentId("project-a")).thenReturn("project-agent");
        serviceReference.set(service);

        assertTrue(adapter.available());
        assertTrue(adapter.exists("project-a"));
        assertEquals("project-agent", adapter.defaultAgentId("project-a"));
    }

    private OpsAgentDefinition definition(String agentId,
                                          int version,
                                          String projectId) {
        return OpsAgentDefinition.builder()
                .agentId(agentId)
                .version(version)
                .lifecycle("PUBLISHED")
                .name("Agent " + agentId)
                .projectId(projectId)
                .engine("CHAT")
                .build();
    }
}
