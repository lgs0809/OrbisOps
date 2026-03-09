package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsPreparationAgentResolverTest {

    @Test
    void frozenWorkSessionAgentOwnsPreparation() {
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAgentDefinition current = agent("analysis-agent", "ANALYZE", List.of(), "project-1");
        when(registry.resolve("analysis-agent")).thenReturn(current);
        OpsPreparationAgentResolver resolver = resolver(registry, null);

        OpsAgentDefinition resolved = resolver.resolve(
                "project-1",
                Map.of("agentId", "analysis-agent", "agentVersion", 1));

        assertEquals("analysis-agent", resolved.getAgentId());
    }

    @Test
    void requestCannotSwitchPreparationAgent() {
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolve("analysis-agent")).thenReturn(
                agent("analysis-agent", "ANALYZE", List.of(), "project-1"));
        OpsPreparationAgentResolver resolver = resolver(registry, null);

        assertThrows(SecurityException.class, () -> resolver.resolve(
                "project-1",
                Map.of(
                        "agentId", "analysis-agent",
                        "preparationGraphId", "custom-prep")));
    }

    @Test
    void projectDefaultIsUsedWhenSnapshotIsAbsent() {
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        ProjectDefinitionApplicationService projectService = mock(ProjectDefinitionApplicationService.class);
        when(projectService.defaultAgentId("project-1")).thenReturn("project-agent");
        when(registry.resolve("project-agent")).thenReturn(
                agent("project-agent", "ANALYZE", List.of(), "project-1"));
        OpsPreparationAgentResolver resolver = resolver(registry, projectService);

        OpsAgentDefinition resolved = resolver.resolve("project-1", Map.of());

        assertEquals("project-agent", resolved.getAgentId());
    }

    @Test
    void frozenVersionMismatchIsRejected() {
        OpsAgentDefinitionQueryGateway registry = mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolve("project-agent")).thenReturn(
                agent("project-agent", "ANALYZE", List.of(), "project-1"));
        OpsPreparationAgentResolver resolver = resolver(registry, null);

        assertThrows(SecurityException.class, () -> resolver.resolve(
                "project-1",
                Map.of("agentId", "project-agent", "agentVersion", 7)));
    }

    @Test
    void noSystemPreparationFallbackExists() {
        OpsPreparationAgentResolver resolver = resolver(
                mock(OpsAgentDefinitionQueryGateway.class),
                null);

        assertThrows(IllegalArgumentException.class, () ->
                resolver.resolve("project-1", Map.of()));
    }

    private OpsPreparationAgentResolver resolver(
            OpsAgentDefinitionQueryGateway registry,
            ProjectDefinitionApplicationService projectService) {
        return new OpsPreparationAgentResolver(registry, () -> projectService);
    }

    private OpsAgentDefinition agent(
            String id,
            String phase,
            List<String> capabilities,
            String projectId) {
        return OpsAgentDefinition.builder()
                .agentId(id)
                .phase(phase)
                .capabilities(capabilities)
                .projectId(projectId)
                .version(1)
                .name(id)
                .build();
    }
}
