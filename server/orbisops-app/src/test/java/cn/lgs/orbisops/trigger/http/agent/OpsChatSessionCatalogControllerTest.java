package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.config.AiClientModelApplicationService;
import cn.lgs.orbisops.trigger.application.ops.OpsAgentDefinitionApplicationService;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.GlobalExceptionHandler;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.http.sse.OpsSseExecutionTemplate;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpsChatSessionCatalogControllerTest {
    private final OpsChatApplicationService application = mock(OpsChatApplicationService.class);
    private final AuthorizeProjectAccessUseCase projectAccess = mock(AuthorizeProjectAccessUseCase.class);
    private final AdminAuthService.AuthPrincipal observer = new AdminAuthService.AuthPrincipal(
            "observer-name", "observer-id", "fixture-jwt", AdminAuthService.SCOPE_USER, false);

    private MockMvc mvc(String path) {
        var executor = mock(ThreadPoolExecutor.class);
        var agents = mock(OpsAgentDefinitionApplicationService.class);
        var sse = mock(OpsSseExecutionTemplate.class);
        Object controller = path.contains("/user/")
                ? new OpsUserChatController(application, projectAccess, agents,
                    mock(AiClientModelApplicationService.class), executor, sse)
                : new OpsChatController(application, executor, projectAccess, agents, sse);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/user/chat", "/api/v1/agent/chat"})
    void boundedCatalogFiltersTheProjectAndIgnoresAClaimedOtherActor(String path) throws Exception {
        when(application.listSessions(null, null, null, null, 100, "observer-id", "project-a"))
                .thenReturn(List.of());
        // The legacy admin-compatible endpoint accepts requestedUserId, but the trusted actor wins.
        when(application.listSessions("forged", null, null, null, 100, "observer-id", "project-a"))
                .thenReturn(List.of());
        mvc(path).perform(get(path + "/sessions").param("projectId", "project-a").param("userId", "forged")
                .param("limit", "100").requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE, observer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0000"));
        verify(application).listSessions(path.contains("/user/") ? null : "forged", null, null, null,
                100, "observer-id", "project-a");
        verifyNoInteractions(projectAccess);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/user/chat", "/api/v1/agent/chat"})
    void omittedProjectKeepsTheOriginalGlobalScope(String path) throws Exception {
        when(application.listSessions(null, null, null, null, 50, "observer-id", null)).thenReturn(List.of());
        mvc(path).perform(get(path + "/sessions").requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE, observer))
                .andExpect(status().isOk());
        verify(application).listSessions(null, null, null, null, 50, "observer-id", null);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/user/chat", "/api/v1/agent/chat"})
    void directReadPreservesTheExistingObserverBoundaryWithoutAddingMembership(String path) throws Exception {
        when(application.readSession("shared", "observer-id", "project-a")).thenReturn(OpsChatSession.builder()
                .sessionId("shared").projectId("project-a").userId("different-owner").build());
        mvc(path).perform(get(path + "/sessions/shared").param("projectId", "project-a")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE, observer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("shared"));
        verify(application).readSession("shared", "observer-id", "project-a");
        verifyNoInteractions(projectAccess);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/user/chat", "/api/v1/agent/chat"})
    void directReadCannotBypassSessionAclOrProjectIdentity(String path) throws Exception {
        when(application.readSession("shared", "observer-id", "project-b"))
                .thenThrow(new SecurityException("SESSION_PROJECT_SCOPE_MISMATCH"));
        when(application.readSession("foreign", "observer-id", "project-a"))
                .thenThrow(new SecurityException("SESSION_READ_FORBIDDEN"));
        var mvc = mvc(path);
        mvc.perform(get(path + "/sessions/shared").param("projectId", "project-b")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE, observer)).andExpect(status().isForbidden());
        mvc.perform(get(path + "/sessions/foreign").param("projectId", "project-a")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE, observer)).andExpect(status().isForbidden());
    }
}
