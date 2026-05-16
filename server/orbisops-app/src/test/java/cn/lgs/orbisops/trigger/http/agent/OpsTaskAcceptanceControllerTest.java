package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.application.episode.TaskAcceptanceApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.GlobalExceptionHandler;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsTaskAcceptanceControllerTest {
    private static final String PATH="/api/v1/user/ops/task-acceptance/task-1";
    private static final String BODY="""
            {"requestId":"request-1234","revision":1,"goalReview":"Check the actual target version","criteria":[]}
            """;
    @Test void projectReadDoesNotImplyAcceptancePermission() throws Exception {
        var service=mock(TaskAcceptanceApplicationService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OpsTaskAcceptanceController(service,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
        var principal=new AdminAuthService.AuthPrincipal("viewer","uid","fixture-token","USER",false);
        doThrow(new SecurityException("PROJECT_ACTION_FORBIDDEN")).when(access).requireAction("p","viewer","uid",false,ProjectAction.APPROVE_CHANGE);
        mvc.perform(get(PATH).param("projectId","p")).andExpect(status().isForbidden());
        mvc.perform(post(PATH).param("projectId","p").contentType(MediaType.APPLICATION_JSON).content(BODY)
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,principal)).andExpect(status().isForbidden());
        mvc.perform(post(PATH+"/draft").param("projectId","p").contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1,\"instruction\":\"核对版本\"}")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,principal)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void naturalDraftUsesAuthenticatedActorAndExistingAcceptancePermission() throws Exception {
        var service=mock(TaskAcceptanceApplicationService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OpsTaskAcceptanceController(service,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post(PATH+"/draft").param("projectId","p").contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1,\"instruction\":\"核对版本\"}")
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,new AdminAuthService.AuthPrincipal("approver","uid","fixture-token","USER",false)))
                .andExpect(status().isOk());
        verify(access).requireAction("p","approver","uid",false,ProjectAction.APPROVE_CHANGE);
        verify(service).draft(eq("p"),eq("task-1"),eq(new TaskAcceptanceApplicationService.NaturalRequest(1,"核对版本")),eq("uid"),eq(false));
    }
    @Test void unresolvedOrChangedRevisionReturnsConflictRatherThanSuccess() throws Exception {
        var service=mock(TaskAcceptanceApplicationService.class);var access=mock(AuthorizeProjectAccessUseCase.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OpsTaskAcceptanceController(service,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.verify(eq("p"),eq("task-1"),any(),eq("uid"),eq(false))).thenThrow(new IllegalStateException("TASK_ACCEPTANCE_REVISION_CHANGED"));
        mvc.perform(post(PATH).param("projectId","p").contentType(MediaType.APPLICATION_JSON).content(BODY)
                .requestAttr(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,new AdminAuthService.AuthPrincipal("approver","uid","fixture-token","USER",false)))
                .andExpect(status().isConflict());
        verify(access).requireAction("p","approver","uid",false,ProjectAction.APPROVE_CHANGE);
    }
}
