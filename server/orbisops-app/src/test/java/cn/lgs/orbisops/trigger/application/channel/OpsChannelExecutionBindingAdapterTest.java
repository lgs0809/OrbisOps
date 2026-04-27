package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelExecutionBindingPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelExecutionBindingAdapterTest {

    @Test
    void reactResolvesProjectDefaultWithoutPersistingItsInternalIdInProductBinding() {
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        when(projects.defaultAgentId("project-1")).thenReturn("default-react");
        when(registry.resolveForProject("default-react", null, false, "project-1"))
                .thenReturn(definition("default-react", "MAIN_ASSISTANT", 4, "react-hash"));
        OpsChannelExecutionBindingAdapter adapter = new OpsChannelExecutionBindingAdapter(registry, projects);

        ChannelExecutionBindingPort.ResolvedExecution result = adapter.resolve("project-1", ExecutionBinding.react());

        assertEquals(ExecutionType.REACT, result.type());
        assertEquals("default-react", result.definitionId());
        assertEquals(4, result.version());
        assertEquals("react-hash", result.definitionHash());
        assertEquals("", ExecutionBinding.react().workflowId());
    }

    @Test
    void workflowLatestResolvesExactPublishedVersionAndRequiresSpecializedWorkflow() {
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        when(registry.resolveForProject("daily-inspection", null, false, "project-1"))
                .thenReturn(definition("daily-inspection", "SPECIALIZED_WORKFLOW", 7, "workflow-hash"));
        OpsChannelExecutionBindingAdapter adapter = new OpsChannelExecutionBindingAdapter(registry, projects);

        ChannelExecutionBindingPort.ResolvedExecution result = adapter.resolve("project-1",
                ExecutionBinding.workflow("daily-inspection", ExecutionVersionPolicy.LATEST_PUBLISHED, null, ""));

        assertEquals(ExecutionType.WORKFLOW, result.type());
        assertEquals(7, result.version());
        assertEquals("workflow-hash", result.definitionHash());
        verify(registry).resolveForProject("daily-inspection", null, false, "project-1");
    }

    @Test
    void workflowCannotPointAtProjectReactDefinition() {
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        when(registry.resolveForProject("default-react", 4, false, "project-1"))
                .thenReturn(definition("default-react", "MAIN_ASSISTANT", 4, "react-hash"));
        OpsChannelExecutionBindingAdapter adapter = new OpsChannelExecutionBindingAdapter(registry, projects);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> adapter.resolve("project-1",
                ExecutionBinding.workflow("default-react", ExecutionVersionPolicy.PINNED_VERSION, 4, "react-hash")));

        assertEquals("CHANNEL_WORKFLOW_REQUIRED", failure.getMessage());
    }

    private OpsAgentDefinition definition(String id, String kind, int version, String hash) {
        return OpsAgentDefinition.builder()
                .agentId(id)
                .projectId("project-1")
                .definitionKind(kind)
                .version(version)
                .definitionHash(hash)
                .engine("GRAPH")
                .name(id)
                .build();
    }
}
