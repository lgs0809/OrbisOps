package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.incident.AppendIncidentTimelineCommand;
import cn.lgs.orbisops.application.incident.CreateIncidentCommand;
import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.incident.IncidentVerificationApplicationService;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.trigger.application.incident.OpsIncidentMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsIncident;
import cn.lgs.orbisops.trigger.ops.OpsIncidentTimelineItem;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsIncidentAdminControllerTest {

    @Test
    void createUsesAuthenticatedPrincipalInsteadOfRequestActor() {
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        OpsIncidentMapper mapper = mock(OpsIncidentMapper.class);
        IncidentVerificationApplicationService verificationService = mock(IncidentVerificationApplicationService.class);
        OpsIncidentAdminController controller = new OpsIncidentAdminController(queries, commands, verificationService, mapper);
        CreateIncidentCommand command = mock(CreateIncidentCommand.class);
        IncidentSnapshot snapshot = mock(IncidentSnapshot.class);
        OpsIncident created = mock(OpsIncident.class);
        when(mapper.createCommand(anyMap())).thenReturn(command);
        when(commands.create(command, "alice")).thenReturn(snapshot);
        when(mapper.incident(snapshot)).thenReturn(created);
        MockHttpServletRequest request = authenticatedRequest("alice");

        OpsIncident result = controller.createIncident(Map.of(
                "projectId", "project-1",
                "title", "database unavailable",
                "actor", "forged-user"), request).getData();

        assertSame(created, result);
        verify(commands).create(command, "alice");
    }

    @Test
    void appendTimelineUsesAuthenticatedPrincipal() {
        IncidentQueryApplicationService queries = mock(IncidentQueryApplicationService.class);
        IncidentCommandApplicationService commands = mock(IncidentCommandApplicationService.class);
        OpsIncidentMapper mapper = mock(OpsIncidentMapper.class);
        IncidentVerificationApplicationService verificationService = mock(IncidentVerificationApplicationService.class);
        OpsIncidentAdminController controller = new OpsIncidentAdminController(queries, commands, verificationService, mapper);
        AppendIncidentTimelineCommand command = mock(AppendIncidentTimelineCommand.class);
        IncidentTimelineEntry entry = mock(IncidentTimelineEntry.class);
        OpsIncidentTimelineItem created = mock(OpsIncidentTimelineItem.class);
        when(mapper.timelineCommand(anyMap())).thenReturn(command);
        when(commands.appendUserTimeline(eq("incident-1"), eq(command), eq("alice"))).thenReturn(entry);
        when(mapper.timeline(entry)).thenReturn(created);
        MockHttpServletRequest request = authenticatedRequest("alice");

        OpsIncidentTimelineItem result = controller.appendIncidentTimeline(
                "incident-1", Map.of("actor", "forged-user"), request).getData();

        assertSame(created, result);
        verify(commands).appendUserTimeline("incident-1", command, "alice");
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
