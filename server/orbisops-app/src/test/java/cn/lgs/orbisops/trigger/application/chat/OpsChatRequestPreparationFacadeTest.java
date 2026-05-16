package cn.lgs.orbisops.trigger.application.chat;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.application.chatsession.OpsChatSessionApplicationFacade;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatRequestPreparationFacadeTest {

    @Test
    void defaultProjectAgentUsesReactExecutionStyle() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        when(projects.exists("demo-project")).thenReturn(true);
        when(projects.defaultAgentId("demo-project")).thenReturn("demo-ops-agent");
        OpsChatRequestPreparationFacade facade = facade(projects, sessionService);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .sessionId("session-1")
                .projectId("demo-project")
                .query("查错误日志")
                .build();

        OpsPreparedChatRequest prepared = facade.prepareRequired(request, "alice", true);

        assertSame(request, prepared.request());
        assertEquals("alice", request.getUserId());
        assertTrue(request.getRunId().startsWith("chat-session-1-"));
        assertEquals("demo-ops-agent", request.getAgentDefinitionId());
        assertEquals("AGENT", request.getMode());
        assertEquals("REACT", request.getMetadata().get("executionStyle"));
        assertEquals("DEFAULT_REACT", request.getMetadata().get("assistantRoute"));
        assertEquals(true, request.getMetadata().get("defaultProjectAgent"));
    }

    @Test
    void manuallySelectedDragDropAgentUsesWorkflowExecutionStyle() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        when(projects.exists("demo-project")).thenReturn(true);
        OpsChatRequestPreparationFacade facade = facade(projects, sessionService);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .agentDefinitionId("fixed-dba-workflow")
                .query("执行巡检流程")
                .build();

        facade.prepareRequired(request, "", false);

        assertEquals("fixed-dba-workflow", request.getAgentDefinitionId());
        assertEquals("WORKFLOW", request.getMode());
        assertEquals("WORKFLOW", request.getMetadata().get("executionStyle"));
        assertEquals("USER_SELECTED_WORKFLOW", request.getMetadata().get("assistantRoute"));
        assertEquals("USER_SELECTED_DRAG_DROP", request.getMetadata().get("workflowSelectionSource"));
        verify(projects, never()).defaultAgentId("demo-project");
    }

    @Test
    void sessionBoundSpecializedWorkflowOverridesProvisionalDefaultReactStyle() {
        OpsChatRequestPreparationFacade facade = facade(
                mock(ProjectDefinitionApplicationService.class),
                mock(OpsChatSessionService.class));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .mode("AGENT")
                .agentDefinition(OpsAgentDefinition.builder()
                        .agentId("workflow-1")
                        .definitionKind("SPECIALIZED_WORKFLOW")
                        .build())
                .build();

        facade.applyBoundAgentExecutionStyle(request);

        assertEquals("WORKFLOW", request.getMode());
        assertEquals("WORKFLOW", request.getMetadata().get("executionStyle"));
        assertEquals("SESSION_BOUND_WORKFLOW", request.getMetadata().get("assistantRoute"));
    }

    @Test
    void preparationDoesNotClassifyOrWriteMemory() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        when(projects.exists("demo-project")).thenReturn(true);
        when(projects.defaultAgentId("demo-project")).thenReturn("demo-project-main");
        OpsChatRequestPreparationFacade facade = facade(projects, mock(OpsChatSessionService.class));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .query("记住索引是 order-*，再查一下问题")
                .build();

        facade.prepareRequired(request, "", false);

        assertEquals(null, request.getMetadata().get("_explicitMemoryApplied"));
        assertEquals(null, request.getMetadata().get("_platformIntentDecision"));
        assertEquals("AGENT", request.getMode());
    }

    @Test
    void projectIsAlwaysRequired() {
        OpsChatRequestPreparationFacade facade = facade(
                mock(ProjectDefinitionApplicationService.class),
                mock(OpsChatSessionService.class));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.prepareRequired(
                        OpsAgentChatRequest.builder().query("你好").build(), "", false));

        assertEquals("请先选择业务系统 projectId", error.getMessage());
    }

    private OpsChatRequestPreparationFacade facade(
            ProjectDefinitionApplicationService projects,
            OpsChatSessionService sessionService) {
        return new OpsChatRequestPreparationFacade(
                projects,
                new OpsChatSessionApplicationFacade(
                        sessionService,
                        projects,
                        mock(GraphEventApplicationService.class)));
    }
}
