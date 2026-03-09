package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAgentDefinitionDraftSaveAdapterTest {

    @Test
    void validatesProjectBindingsAndProjectsAuditAction() {
        OpsAgentCapabilityApplicationService capabilities =
                mock(OpsAgentCapabilityApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .build();
        OpsAgentDefinitionDraftSaveAdapter adapter =
                new OpsAgentDefinitionDraftSaveAdapter(
                        capabilities,
                        new OpsAgentDefinitionExecutionShapeMapper(),
                        audit);

        adapter.assertProjectAndBindingsValid(definition);
        adapter.recordDraftSave("save-draft", definition);

        verify(capabilities).requireExistingProject("project-1");
        verify(capabilities).assertValid(definition);
        verify(audit).record(
                "agent-definition",
                "save-draft",
                "agent-1",
                null,
                definition);
    }

    @Test
    void missingProjectPreservesCompatibilityError() {
        OpsAgentDefinitionDraftSaveAdapter adapter =
                new OpsAgentDefinitionDraftSaveAdapter(
                        mock(OpsAgentCapabilityApplicationService.class),
                        new OpsAgentDefinitionExecutionShapeMapper(),
                        mock(OpsConfigAuditService.class));
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("")
                .build();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.assertProjectAndBindingsValid(definition));

        assertEquals(
                "Agent 必须归属一个项目；跨项目复用请使用复制功能",
                error.getMessage());
    }
}
