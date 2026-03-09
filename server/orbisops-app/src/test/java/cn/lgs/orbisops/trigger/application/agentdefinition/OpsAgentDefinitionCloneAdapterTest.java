package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionCloneAdapterTest {

    @Test
    void resolvesSanitizesAndPreparesMutableCloneDraft() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsAgentCapabilityApplicationService capabilities =
                mock(OpsAgentCapabilityApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition source = OpsAgentDefinition.builder()
                .agentId("source")
                .projectId("project-1")
                .name("源 Agent")
                .version(4)
                .lifecycle("PUBLISHED")
                .source("API")
                .build();
        when(capabilities.requireExistingProject("project-2")).thenReturn("project-2");
        when(definitions.resolve("source", null, true)).thenReturn(source);
        OpsAgentDefinitionCloneAdapter adapter = new OpsAgentDefinitionCloneAdapter(
                definitions,
                capabilities,
                new OpsAgentDefinitionExecutionShapeMapper(),
                audit);

        assertEquals("project-2", adapter.requireExistingProject("project-2"));
        assertSame(source, adapter.resolveSource("source"));
        assertSame(source, adapter.sanitizeForProject(source, "project-2"));
        OpsAgentDefinition prepared = adapter.prepareClone(
                source,
                "project-2",
                "target",
                "");

        assertSame(source, prepared);
        assertEquals("target", prepared.getAgentId());
        assertEquals("project-2", prepared.getProjectId());
        assertEquals("源 Agent 副本", prepared.getName());
        assertNull(prepared.getVersion());
        assertEquals("DRAFT", prepared.getLifecycle());
        assertEquals("UI", prepared.getSource());
        verify(capabilities).sanitizeForProject(source, "project-2");
    }

    @Test
    void explicitNameAndAuditProjectionPreserveLegacyContract() {
        OpsAgentDefinitionGateway definitions = mock(OpsAgentDefinitionGateway.class);
        OpsAgentCapabilityApplicationService capabilities =
                mock(OpsAgentCapabilityApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition clone = OpsAgentDefinition.builder()
                .name("old")
                .build();
        OpsAgentDefinitionCloneAdapter adapter = new OpsAgentDefinitionCloneAdapter(
                definitions,
                capabilities,
                new OpsAgentDefinitionExecutionShapeMapper(),
                audit);

        adapter.prepareClone(clone, "project-2", "target", "  新 Agent  ");
        adapter.recordClone("source", "target", clone);

        assertEquals("新 Agent", clone.getName());
        verify(audit).record(
                "agent-definition",
                "clone",
                "target",
                null,
                clone);
    }
}
