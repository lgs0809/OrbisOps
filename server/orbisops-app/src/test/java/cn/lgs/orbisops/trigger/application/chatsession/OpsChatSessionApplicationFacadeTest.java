package cn.lgs.orbisops.trigger.application.chatsession;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionCreateRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionUpdateRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatSessionApplicationFacadeTest {

    @Test
    void createBindsScopedUserAndProjectDefaultAgent() {
        OpsChatSessionService sessions = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(projects.exists("demo-project")).thenReturn(true);
        when(projects.defaultAgentId("demo-project")).thenReturn("demo-ops-agent");
        when(sessions.create(any())).thenReturn(OpsChatSession.builder()
                .sessionId("session-1")
                .build());
        OpsChatSessionApplicationFacade facade = new OpsChatSessionApplicationFacade(
                sessions,
                projects,
                events);
        OpsChatSessionCreateRequest request = new OpsChatSessionCreateRequest();
        request.setProjectId("demo-project");
        request.setMode("MULTI_TURN");

        facade.create(request, "alice");

        ArgumentCaptor<OpsChatSessionCreateRequest> captor =
                ArgumentCaptor.forClass(OpsChatSessionCreateRequest.class);
        verify(sessions).create(captor.capture());
        OpsChatSessionCreateRequest actual = captor.getValue();
        assertEquals("alice", actual.getUserId());
        assertEquals("demo-ops-agent", actual.getAgentId());
        assertEquals("AGENT", actual.getMode());
        assertEquals("demo-project", actual.getMetadata().get("projectId"));
        assertEquals(true, actual.getMetadata().get("defaultProjectAgent"));
    }

    @Test
    void readFacadeEnforcesParticipantAccessBeforeLoadingMessages() {
        OpsChatSessionService sessions = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        OpsChatSession session = OpsChatSession.builder()
                .sessionId("session-1")
                .userId("bob")
                .build();
        when(sessions.get("session-1")).thenReturn(Optional.of(session));
        when(sessions.canRead("session-1", "alice")).thenReturn(false);
        OpsChatSessionApplicationFacade facade = new OpsChatSessionApplicationFacade(
                sessions,
                projects,
                events);

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> facade.messages("session-1", 10, "alice"));

        assertEquals("SESSION_READ_FORBIDDEN", error.getMessage());
    }

    @Test
    void firstWriteAllowsMissingSessionButExistingSessionRequiresAcl() {
        OpsChatSessionService sessions = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(sessions.get("new-session")).thenReturn(Optional.empty());
        OpsChatSession existing = OpsChatSession.builder()
                .sessionId("existing")
                .userId("bob")
                .build();
        when(sessions.get("existing")).thenReturn(Optional.of(existing));
        when(sessions.canWrite("existing", "alice")).thenReturn(false);
        OpsChatSessionApplicationFacade facade = new OpsChatSessionApplicationFacade(
                sessions,
                projects,
                events);

        assertDoesNotThrow(() -> facade.assertWrite("new-session", "alice"));
        SecurityException error = assertThrows(
                SecurityException.class,
                () -> facade.assertWrite("existing", "alice"));

        assertEquals("SESSION_WRITE_FORBIDDEN", error.getMessage());
    }

    @Test
    void ownerMayUpdateAndTouchDelegatesResponseSummary() {
        OpsChatSessionService sessions = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        OpsChatSession session = OpsChatSession.builder()
                .sessionId("session-1")
                .userId("alice")
                .title("title")
                .build();
        when(sessions.get("session-1")).thenReturn(Optional.of(session));
        when(sessions.update(any(), any())).thenReturn(session);
        when(sessions.messages("session-1", 200)).thenReturn(List.<OpsChatMessageView>of());
        OpsChatSessionApplicationFacade facade = new OpsChatSessionApplicationFacade(
                sessions,
                projects,
                events);

        OpsChatSession updated = facade.update(
                "session-1",
                new OpsChatSessionUpdateRequest(),
                "alice");

        assertEquals(session, updated);
        assertTrue(facade.messages("session-1", null, "").isEmpty());
        verify(sessions).update(eq("session-1"), any());
    }
}
